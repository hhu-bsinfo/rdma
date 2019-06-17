package de.hhu.bsinfo.neutrino.example.command;

import de.hhu.bsinfo.neutrino.buffer.RegisteredBuffer;
import de.hhu.bsinfo.neutrino.buffer.RegisteredBufferWindow;
import de.hhu.bsinfo.neutrino.buffer.RemoteBuffer;
import de.hhu.bsinfo.neutrino.data.NativeByte;
import de.hhu.bsinfo.neutrino.data.NativeLong;
import de.hhu.bsinfo.neutrino.example.util.ContextMonitorThread;
import de.hhu.bsinfo.neutrino.api.request.CompletionManager;
import de.hhu.bsinfo.neutrino.struct.Struct;
import de.hhu.bsinfo.neutrino.util.CustomStruct;
import de.hhu.bsinfo.neutrino.verbs.*;
import de.hhu.bsinfo.neutrino.verbs.CompletionQueue.WorkCompletionArray;
import de.hhu.bsinfo.neutrino.verbs.ExtendedCompletionQueue.InitialAttributes;
import de.hhu.bsinfo.neutrino.verbs.ExtendedCompletionQueue.PollAttributes;
import de.hhu.bsinfo.neutrino.verbs.ExtendedCompletionQueue.WorkCompletionCapability;
import de.hhu.bsinfo.neutrino.verbs.ExtendedConnectionDomain.OperationFlag;
import de.hhu.bsinfo.neutrino.verbs.ExtendedDeviceAttributes.QueryExtendedDeviceInput;
import de.hhu.bsinfo.neutrino.verbs.QueuePair.AttributeFlag;
import de.hhu.bsinfo.neutrino.verbs.QueuePair.Attributes;
import de.hhu.bsinfo.neutrino.verbs.QueuePair.State;
import de.hhu.bsinfo.neutrino.verbs.QueuePair.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import de.hhu.bsinfo.neutrino.verbs.SharedReceiveQueue.ExtendedAttributeFlag;
import de.hhu.bsinfo.neutrino.verbs.SharedReceiveQueue.ExtendedInitialAttributes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

@CommandLine.Command(
    name = "start",
    description = "Starts a new neutrino instance.%n",
    showDefaultValues = true,
    separator = " ")
public class Start implements Callable<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Start.class);

    private static final int DEFAULT_QUEUE_SIZE = 100;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final int DEFAULT_SERVER_PORT = 2998;

    private static final long MAGIC_NUMBER = 0xC0FEFE;
    private static final int INTERVAL = 1000;

    private PortAttributes port;
    private ExtendedConnectionDomain extendedConnectionDomain;
    private ProtectionDomain protectionDomain;

    private MonitoringData monitoringData;
    private RegisteredBuffer localBuffer;
    private RemoteBuffer remoteBuffer;

    private CompletionChannel completionChannel;

    private ExtendedCompletionQueue extendedCompletionQueue;
    private PollAttributes pollAttributes = new PollAttributes();

    private CompletionQueue completionQueue;
    private SharedReceiveQueue sharedReceiveQueue;
    private QueuePair queuePair;

    private ConnectionInfo remoteInfo;

    private CompletionManager completionManager;

    @CommandLine.Option(
        names = "--server",
        description = "Runs this instance in server mode.")
    private boolean isServer;

    @CommandLine.Option(
        names = {"-p", "--port"},
        description = "The port the server will listen on.")
    private int portNumber = DEFAULT_SERVER_PORT;

    @CommandLine.Option(
        names = {"-b", "--buffer-size"},
        description = "Sets the memory regions buffer size.")
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    @CommandLine.Option(
        names = {"--use-extended-api", "-e"},
        description = "Set to true to enable the extended verbs api.")
    private boolean useExtendedApi = false;

    @CommandLine.Option(
        names = {"--use-completion-channel", "-ch"},
        description = "Set to true to wait for completion events using a completion channel,"
            + "instead of constantly polling the completion queue.")
    private boolean useCompletionChannel = false;

    @CommandLine.Option(
        names = {"-c", "--connect"},
        description = "The server to connect to.")
    private InetSocketAddress serverAddress;

    @CommandLine.Option(
            names = {"--bind"},
            description = "The servers bind address.")
    private InetSocketAddress bindAddress = new InetSocketAddress(DEFAULT_SERVER_PORT);

    @Override
    public Void call() throws Exception {
        if (!isServer && serverAddress == null) {
            LOGGER.error("Please specify the server address");
            return null;
        }

        int numDevices = DeviceAttributes.getDeviceCount();

        if(numDevices <= 0) {
            LOGGER.error("No RDMA devices were found in your system");
            return null;
        }

        Context context = Context.openDevice(0);
        LOGGER.info("Opened context for device {}", context.getDeviceName());

        if(useExtendedApi) {
            var attributes = new ExtendedConnectionDomain.InitialAttributes.Builder(OperationFlag.O_CREAT).build();

            extendedConnectionDomain = context.openExtendedConnectionDomain(attributes);
            LOGGER.info("Opened extended connection domain");
        }

        ContextMonitorThread contextMonitor = new ContextMonitorThread(context);
        contextMonitor.start();

        if(useExtendedApi) {
            ExtendedDeviceAttributes device = context.queryExtendedDevice(new QueryExtendedDeviceInput());
            LOGGER.info(device.toString());
        } else {
            DeviceAttributes device = context.queryDevice();
            LOGGER.info(device.toString());
        }

        port = context.queryPort(1);
        LOGGER.info(port.toString());

        protectionDomain = context.allocateProtectionDomain();
        LOGGER.info("Allocated protection domain");

        localBuffer = protectionDomain.allocateMemory(DEFAULT_BUFFER_SIZE, AccessFlag.LOCAL_WRITE, AccessFlag.REMOTE_READ, AccessFlag.REMOTE_WRITE, AccessFlag.MW_BIND);
        LOGGER.info(localBuffer.toString());
        LOGGER.info("Registered local buffer");

        monitoringData = localBuffer.getObject(0, MonitoringData::new);

        if(useCompletionChannel) {
            completionChannel = context.createCompletionChannel();
            LOGGER.info("Created completion channel");
        }

        if(useExtendedApi) {
            InitialAttributes attributes = new InitialAttributes.Builder(DEFAULT_QUEUE_SIZE)
                    .withCompletionChannel(completionChannel)
                    .withWorkCompletionCapabilities(WorkCompletionCapability.WITH_COMPLETION_TIMESTAMP)
                    .build();

            extendedCompletionQueue = context.createExtendedCompletionQueue(attributes);
            completionQueue = Objects.requireNonNull(extendedCompletionQueue).toCompletionQueue();
            LOGGER.info("Created extended completion queue");
        } else {
            completionQueue = context.createCompletionQueue(DEFAULT_QUEUE_SIZE, completionChannel);
            LOGGER.info("Created completion queue");
        }

        completionManager = new CompletionManager(completionQueue);

        if(useExtendedApi) {
            sharedReceiveQueue = context.createExtendedSharedReceiveQueue(
                    new ExtendedInitialAttributes.Builder(protectionDomain, DEFAULT_QUEUE_SIZE, 1).build());

            LOGGER.info("Created extended shared receive queue");
        } else {
            sharedReceiveQueue = protectionDomain.createSharedReceiveQueue(
                    new SharedReceiveQueue.InitialAttributes.Builder(DEFAULT_QUEUE_SIZE, 1).build());

            LOGGER.info("Created shared receive queue");
        }

        //testWorkQueue(context);

        if (isServer) {
            startServer();
        } else {
            startClient();
        }

        queuePair.close();
        completionQueue.close();
        sharedReceiveQueue.close();
        completionChannel.close();
        localBuffer.close();
        protectionDomain.close();
        contextMonitor.finish();
        context.close();

        return null;
    }

    private void testMemoryWindow() {
        RegisteredBufferWindow window = localBuffer.allocateAndBindMemoryWindow(queuePair, 0, 4, AccessFlag.LOCAL_WRITE, AccessFlag.REMOTE_READ, AccessFlag.REMOTE_WRITE);
        if (window == null) {
            return;
        }

        poll();
        LOGGER.debug("Allocated and bound memory window");
        LOGGER.debug(window.toString());

        window.close();
        LOGGER.debug("Deallocated memory window");
    }

    private void testWorkQueue(final Context context) {
        var workQueue = context.createWorkQueue(
                new WorkQueue.InitialAttributes.Builder(DEFAULT_QUEUE_SIZE, 1, WorkQueue.Type.RQ, protectionDomain, completionQueue).build());

        if(workQueue == null) {
            return;
        }

        LOGGER.debug("Created Work queue");

        workQueue.modify(new WorkQueue.Attributes.Builder()
                .withCurrentState(WorkQueue.State.RESET)
                .withState(WorkQueue.State.READY)
                .build());

        LOGGER.debug("Modified Work queue");

        ReceiveWorkQueueIndirectionTable indirectionTable = context.createReceiveWorkQueueIndirectionTable(
                new ReceiveWorkQueueIndirectionTable.InitialAttributes(0,
                        config -> config.setWorkQueue(0, workQueue)));

        if(indirectionTable == null) {
            return;
        }

        LOGGER.debug("Created receive work queue indirection table");

        indirectionTable.close();

        LOGGER.debug("Destroyed receive work queue indirection table");

        workQueue.close();

        LOGGER.debug("Destroyed work queue");
    }

    private void startClient() throws IOException, InterruptedException {
        var socket = new Socket(serverAddress.getAddress(), serverAddress.getPort());

        queuePair = createQueuePair(socket);

        //testMemoryWindow();

        startMonitoring();
    }

    private void startServer() throws IOException, InterruptedException {
        var serverSocket = new ServerSocket(portNumber);
        var socket = serverSocket.accept();

        queuePair = createQueuePair(socket);

        //testMemoryWindow();

        readMonitoringData();
    }

    private QueuePair createQueuePair(Socket socket) throws IOException {
        queuePair = protectionDomain.createQueuePair(new QueuePair.InitialAttributes.Builder(
                Type.RC, completionQueue, completionQueue, DEFAULT_QUEUE_SIZE, DEFAULT_BUFFER_SIZE, 1, 1)
                .build());


        LOGGER.info("Created queue pair!");

        queuePair.modify(QueuePair.Attributes.Builder.buildInitAttributesRC(
                (short) 0, (byte) 1, AccessFlag.LOCAL_WRITE, AccessFlag.REMOTE_WRITE, AccessFlag.REMOTE_READ));

        LOGGER.info("Queue pair transitioned to INIT state!");

        var localInfo = new ConnectionInfo(port.getLocalId(), queuePair.getQueuePairNumber(), localBuffer);
        remoteInfo = exchangeInfo(socket, localInfo);

        remoteBuffer = new RemoteBuffer(queuePair, remoteInfo.getRemoteAddress(), remoteInfo.getCapacity(), remoteInfo.getRemoteKey(), completionManager);

        LOGGER.info(remoteBuffer.toString());

        queuePair.modify(QueuePair.Attributes.Builder.buildReadyToReceiveAttributesRC(
                remoteInfo.getQueuePairNumber(), remoteInfo.getLocalId(), (byte) 1));

        LOGGER.info("Queue pair transitioned to RTR state");

        queuePair.modify(QueuePair.Attributes.Builder.buildReadyToSendAttributesRC());

        LOGGER.info("Queue pair transitioned to RTS state");

        return queuePair;
    }

    private void readMonitoringData() throws InterruptedException {
        while (true) {
            long workRequestId = localBuffer.read(0, remoteBuffer, 0, monitoringData.getNativeSize());
            completionManager.await(workRequestId);
            LOGGER.info(monitoringData.toString());
            Thread.sleep(INTERVAL);
        }
    }

    private void startMonitoring() throws InterruptedException {
        monitoringData.clear();
        while (true) {
            monitoringData.setCpuUsage((byte) Math.abs(ThreadLocalRandom.current().nextInt() % 100));
            monitoringData.setTimestamp(System.currentTimeMillis());
            monitoringData.setWriteCount(monitoringData.getWriteCount() + 1);
            Thread.sleep(INTERVAL);
        }
    }

    private void poll() {
        if(useCompletionChannel) {
            completionQueue.requestNotification(false);
            CompletionQueue eventQueue = completionChannel.getCompletionEvent();

            if(useExtendedApi) {
                // Poll the completion queue
                extendedCompletionQueue.startPolling(pollAttributes);

                // Poll all work completions from the completion queue
                do {
                    LOGGER.debug("Status = {}", extendedCompletionQueue.getStatus());
                    LOGGER.debug("OpCode = {}", extendedCompletionQueue.readOpCode());
                    LOGGER.debug("Timestamp = {}", extendedCompletionQueue.readCompletionTimestamp());
                } while (extendedCompletionQueue.pollNext());

                // Stop polling the completion queue
                extendedCompletionQueue.stopPolling();

                Objects.requireNonNull(eventQueue).acknowledgeEvents(1);
            } else {
                var completionArray = new WorkCompletionArray(DEFAULT_QUEUE_SIZE);

                Objects.requireNonNull(eventQueue).poll(completionArray);

                for (int i = 0; i < completionArray.getLength(); i++) {
                    LOGGER.debug("Status = {}", completionArray.get(i).getStatus());
                    LOGGER.debug("OpCode = {}", completionArray.get(i).getOpCode());
                }

                completionQueue.acknowledgeEvents(1);
            }
        } else {
            if (useExtendedApi) {
                // Poll the completion queue until a work completion is available
                while (!extendedCompletionQueue.startPolling(pollAttributes));

                // Poll all work completions from the completion queue
                do {
                    LOGGER.debug("Status = {}", extendedCompletionQueue.getStatus());
                    LOGGER.debug("OpCode = {}", extendedCompletionQueue.readOpCode());
                    LOGGER.debug("Timestamp = {}", extendedCompletionQueue.readCompletionTimestamp());
                } while (extendedCompletionQueue.pollNext());

                // Stop polling the completion queue
                extendedCompletionQueue.stopPolling();
            } else {
                var completionArray = new WorkCompletionArray(DEFAULT_QUEUE_SIZE);

                while (completionArray.getLength() == 0) {
                    completionQueue.poll(completionArray);
                }

                for(int i = 0; i < completionArray.getLength(); i++) {
                    LOGGER.debug("Status = {}", completionArray.get(i).getStatus());
                    LOGGER.debug("OpCode = {}", completionArray.get(i).getOpCode());
                }
            }
        }
    }

    private static ConnectionInfo exchangeInfo(Socket socket, ConnectionInfo localInfo) throws IOException {

        LOGGER.info("Sending connection info {}", localInfo);
        socket.getOutputStream().write(ByteBuffer.allocate(Short.BYTES + 2 * Integer.BYTES + 2 * Long.BYTES)
            .putShort(localInfo.getLocalId())
            .putInt(localInfo.getQueuePairNumber())
            .putInt(localInfo.getRemoteKey())
            .putLong(localInfo.getRemoteAddress())
            .putLong(localInfo.getCapacity())
            .array());

        LOGGER.info("Waiting for remote connection info");
        var byteBuffer = ByteBuffer.wrap(socket.getInputStream().readNBytes(Short.BYTES + 2 * Integer.BYTES + 2 *Long.BYTES));

        var remoteInfo = new ConnectionInfo(byteBuffer);

        LOGGER.info("Received connection info {}", remoteInfo);

        return remoteInfo;
    }

    private static class ConnectionInfo {
        private final short localId;
        private final int queuePairNumber;
        private final int remoteKey;
        private final long remoteAddress;
        private final long capacity;

        ConnectionInfo(short localId, int queuePairNumber, RegisteredBuffer buffer) {
            this.localId = localId;
            this.queuePairNumber = queuePairNumber;
            remoteKey = buffer.getRemoteKey();
            remoteAddress = buffer.getHandle();
            capacity = buffer.capacity();
        }

        ConnectionInfo(ByteBuffer buffer) {
            localId = buffer.getShort();
            queuePairNumber = buffer.getInt();
            remoteKey = buffer.getInt();
            remoteAddress = buffer.getLong();
            capacity = buffer.getLong();
        }

        short getLocalId() {
            return localId;
        }

        int getQueuePairNumber() {
            return queuePairNumber;
        }

        int getRemoteKey() {
            return remoteKey;
        }

        long getRemoteAddress() {
            return remoteAddress;
        }

        public long getCapacity() {
            return capacity;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ConnectionInfo.class.getSimpleName() + "[", "]")
                .add("localId=" + localId)
                .add("queuePairNumber=" + queuePairNumber)
                .add("remoteKey=" + remoteKey)
                .add("remoteAddress=" + remoteAddress)
                .add("capacity=" + capacity)
                .toString();
        }
    }

    @CustomStruct(Byte.BYTES + 2 * Long.BYTES)
    private static class MonitoringData extends Struct {
        private  NativeByte cpuUsage = byteField();
        private  NativeLong timestamp = longField();
        private  NativeLong writeCount = longField();

        public MonitoringData(long handle) {
            super(handle);
        }

        public byte getCpuUsage() {
            return cpuUsage.get();
        }

        public void setCpuUsage(byte cpuUsage) {
            this.cpuUsage.set(cpuUsage);
        }

        public long getTimestamp() {
            return timestamp.get();
        }

        public void setTimestamp(long timestamp) {
            this.timestamp.set(timestamp);
        }

        public long getWriteCount() {
            return writeCount.get();
        }

        public void setWriteCount(long writeCount) {
            this.writeCount.set(writeCount);
        }

        @Override
        public String toString() {
            return "MonitoringData {" +
                    "\n\tcpuUsage=" + cpuUsage +
                    ",\n\ttimestamp=" + timestamp +
                    ",\n\twriteCount=" + writeCount +
                    "\n}";
        }
    }
}
