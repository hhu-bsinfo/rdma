package de.hhu.bsinfo.neutrino.api.network.impl;

import de.hhu.bsinfo.neutrino.api.device.InfinibandDevice;
import de.hhu.bsinfo.neutrino.api.device.InfinibandDeviceConfig;
import de.hhu.bsinfo.neutrino.api.network.Connection;
import de.hhu.bsinfo.neutrino.api.network.Negotiator;
import de.hhu.bsinfo.neutrino.api.network.NetworkConfiguration;
import de.hhu.bsinfo.neutrino.api.network.impl.agent.AgentResources;
import de.hhu.bsinfo.neutrino.api.network.impl.buffer.BufferPool;
import de.hhu.bsinfo.neutrino.api.network.impl.util.QueuePairResources;
import de.hhu.bsinfo.neutrino.api.network.impl.util.QueuePairState;
import de.hhu.bsinfo.neutrino.api.util.QueuePairAddress;
import de.hhu.bsinfo.neutrino.util.EventFileDescriptor;
import de.hhu.bsinfo.neutrino.verbs.*;
import lombok.extern.slf4j.Slf4j;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ConnectionManager {

    private static final int MAX_CONNECTIONS = 1024;

    private final InfinibandDevice device;

    private final InfinibandDeviceConfig deviceConfig;

    private final NetworkConfiguration networkConfig;

    private final AtomicInteger connectionCounter = new AtomicInteger();

    private final SharedResources sharedResources;

    // TODO(krakowski):
    //  let connections array grow dynamically
    private final InternalConnection[] connections = new InternalConnection[MAX_CONNECTIONS];

    public ConnectionManager(SharedResources sharedResources) {
        device = sharedResources.device();
        deviceConfig = sharedResources.deviceConfig();
        networkConfig = sharedResources.networkConfig();
        this.sharedResources = sharedResources;
    }

    public InternalConnection connect(Negotiator negotiator, Mtu mtu, SharedReceiveQueue receiveQueue, AgentResources sendResources, AgentResources receiveResources) {


        var sendProtectionDomain = sendResources.protectionDomain();
        var receiveProtectionDomain = receiveResources.protectionDomain();

        // Create resources for new queue pair
        var queuePairResources = QueuePairResources.create(device, networkConfig);

        // Create initial attributes
        var initialAttributes = createInitialAttributes(queuePairResources, receiveQueue);

        // Create queue pair
        var queuePair = createQueuePair(initialAttributes, sendProtectionDomain);

        // Create new connection
        var connection = createConnection(queuePair, queuePairResources);

        // Exchange queue pair information with remote peer
        var remote = exchangeInfo(negotiator, connection);

        // Connect to the remote's queue pair
        connect(queuePair, remote, mtu);

        log.debug("Established connection with {}:{}", remote.getLocalId(), remote.getQueuePairNumber());

        connections[connection.getId()] = connection;

        return connection;
    }

    public InternalConnection get(Connection connection) {
        return get(connection.getId());
    }

    private InternalConnection get(int id) {
        return Objects.requireNonNull(connections[id], "Connection does not exit");
    }

    private QueuePair.InitialAttributes createInitialAttributes(QueuePairResources queuePairResources, SharedReceiveQueue receiveQueue) {
        return new QueuePair.InitialAttributes.Builder(
                QueuePair.Type.RC,
                queuePairResources.getSendCompletionQueue(),
                queuePairResources.getReceiveCompletionQueue(),
                networkConfig.getQueuePairSize(),
                networkConfig.getQueuePairSize(),
                networkConfig.getMaxScatterGatherElements(),
                networkConfig.getMaxScatterGatherElements()
        ).withSharedReceiveQueue(receiveQueue).build();
    }

    private QueuePair createQueuePair(QueuePair.InitialAttributes initialAttributes, ProtectionDomain protectionDomain) {
        var queuePair = protectionDomain.createQueuePair(initialAttributes);
        queuePair.modify(new QueuePair.Attributes.Builder()
                .withState(QueuePair.State.INIT)
                .withPartitionKeyIndex((short) 0)
                .withPortNumber(deviceConfig.getPortNumber())
                .withAccessFlags(AccessFlag.LOCAL_WRITE, AccessFlag.REMOTE_WRITE, AccessFlag.REMOTE_READ));

        return queuePair;
    }

    private InternalConnection createConnection(QueuePair queuePair, QueuePairResources queuePairResources) {

        // Query queue pair attributes to set initial queue pair state
        var attributes = queuePair.queryAttributes(QueuePair.AttributeFlag.CAP);
        var state = new QueuePairState(attributes.capabilities.getMaxSendWorkRequests(), 0);

        // Create event file descriptor for tracking free space on the queue pair
        var queueDescriptor = EventFileDescriptor.create(attributes.capabilities.getMaxSendWorkRequests(), EventFileDescriptor.OpenMode.NONBLOCK);

        // Get the next connection id
        var id = connectionCounter.getAndIncrement();

        log.debug("Creating new connection #{}", id);

        log.debug("  | eventfd - 0x{} - {}",
                Long.toHexString(queueDescriptor.getHandle()),
                Arrays.toString(queueDescriptor.getFlags()));

        log.debug("  | sendcc  - 0x{} - {}",
                Long.toHexString(queuePairResources.getSendFileDescriptor().getHandle()),
                Arrays.toString(queuePairResources.getSendFileDescriptor().getFlags()));

        log.debug("  | rcvcc   - 0x{} - {}",
                Long.toHexString(queuePairResources.getReceiveFileDescriptor().getHandle()),
                Arrays.toString(queuePairResources.getReceiveFileDescriptor().getFlags()));

        // Create a new connection
        return InternalConnection.builder()
                .id(id)
                .localId(device.getPortAttributes().getLocalId())
                .portNumber(deviceConfig.getPortNumber())
                .queuePair(queuePair)
                .resources(queuePairResources)
                .state(state)
                .queueFileDescriptor(queueDescriptor)
                .build();
    }

    private static QueuePairAddress exchangeInfo(Negotiator negotiator, InternalConnection connection) {
        return negotiator.exchange(QueuePairAddress.builder()
                .localId(connection.getLocalId())
                .portNumber(connection.getPortNumber())
                .queuePairNumber(connection.getQueuePair().getQueuePairNumber()).build());
    }

    private void connect(QueuePair queuePair, QueuePairAddress remote, Mtu mtu) {
        queuePair.modify(QueuePair.Attributes.Builder
                .buildReadyToReceiveAttributesRC(remote.getQueuePairNumber(), remote.getLocalId(), remote.getPortNumber())
                .withPathMtu(device.getPortAttributes().getMaxMtu())
                .withReceivePacketNumber(0)
                .withMaxDestinationAtomicReads((byte) 1)
                .withMinRnrTimer(networkConfig.getRnrTimer())
                .withServiceLevel(networkConfig.getServiceLevel())
                .withSourcePathBits((byte) 0)
                .withIsGlobal(false));

        queuePair.modify(QueuePair.Attributes.Builder.buildReadyToSendAttributesRC()
                .withTimeout(networkConfig.getTimeout())
                .withRetryCount(networkConfig.getRetryCount())
                .withRnrRetryCount(networkConfig.getRnrRetryCount()));
    }

}
