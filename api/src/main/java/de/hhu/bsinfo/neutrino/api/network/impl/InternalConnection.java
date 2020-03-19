package de.hhu.bsinfo.neutrino.api.network.impl;

import de.hhu.bsinfo.neutrino.api.network.impl.agent.ReceiveAgent;
import de.hhu.bsinfo.neutrino.api.network.impl.agent.SendAgent;
import de.hhu.bsinfo.neutrino.api.network.impl.subscriber.OperationSubscriber;
import de.hhu.bsinfo.neutrino.api.network.impl.util.QueuePairResources;
import de.hhu.bsinfo.neutrino.api.network.impl.util.QueuePairState;
import de.hhu.bsinfo.neutrino.util.EventFileDescriptor;
import de.hhu.bsinfo.neutrino.verbs.Endpoint;
import de.hhu.bsinfo.neutrino.verbs.QueuePair;
import lombok.Builder;
import lombok.Data;
import org.agrona.collections.ArrayUtil;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

@Builder
public @Data class InternalConnection {

    private final int id;

    private final short localId;

    private final byte portNumber;

    private final QueuePair queuePair;

    private final QueuePairResources resources;

    private final QueuePairState state;

    private final EventFileDescriptor queueFileDescriptor;

    private volatile SendAgent sendAgent;
    private static final AtomicReferenceFieldUpdater<InternalConnection, SendAgent> SEND_AGENT =
            AtomicReferenceFieldUpdater.newUpdater(InternalConnection.class, SendAgent.class, "sendAgent");

    private volatile ReceiveAgent receiveAgent;
    private static final AtomicReferenceFieldUpdater<InternalConnection, ReceiveAgent> RECEIVE_AGENT =
            AtomicReferenceFieldUpdater.newUpdater(InternalConnection.class, ReceiveAgent.class, "receiveAgent");

    @Builder.Default
    private volatile OperationSubscriber[] subscribers = new OperationSubscriber[0];
    private static final AtomicReferenceFieldUpdater<InternalConnection, OperationSubscriber[]> SUBSCRIBERS =
            AtomicReferenceFieldUpdater.newUpdater(InternalConnection.class, OperationSubscriber[].class, "subscribers");

    public void setSendAgent(SendAgent agent) {
        SEND_AGENT.set(this, agent);
    }

    public void setReceiveAgent(ReceiveAgent agent) {
        RECEIVE_AGENT.set(this, agent);
    }

    public void addSubscriber(OperationSubscriber subscriber) {
        OperationSubscriber[] oldArray;
        OperationSubscriber[] newArray;

        do {
            oldArray = subscribers;
            newArray = ArrayUtil.add(oldArray, subscriber);
        } while (!SUBSCRIBERS.compareAndSet(this, oldArray, newArray));
    }

    public void removeSubscriber(OperationSubscriber subscriber) {
        OperationSubscriber[] oldArray;
        OperationSubscriber[] newArray;

        do {
            oldArray = subscribers;
            newArray = ArrayUtil.remove(oldArray, subscriber);
        } while (!SUBSCRIBERS.compareAndSet(this, oldArray, newArray));
    }

    public int getFreeSlots() {
        return (int) queueFileDescriptor.read();
    }

    public void incrementFreeSlots(int value) {
        queueFileDescriptor.increment(value);
    }
}
