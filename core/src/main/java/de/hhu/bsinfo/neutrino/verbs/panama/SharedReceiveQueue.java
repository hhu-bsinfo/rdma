package de.hhu.bsinfo.neutrino.verbs.panama;

import static org.linux.rdma.ibverbs_h.*;

import de.hhu.bsinfo.neutrino.verbs.panama.util.Struct;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;

public final class SharedReceiveQueue extends Struct {

    public SharedReceiveQueue() {
        super(ibv_srq.allocate());
    }

    public SharedReceiveQueue(MemoryAddress address) {
        super(address, ibv_srq.$LAYOUT());
    }

    public MemoryAddress getContext() {
        return ibv_srq.context$get(segment());
    }

    public MemoryAddress getSharedReceiveQueueContext() {
        return ibv_srq.srq_context$get(segment());
    }

    public MemoryAddress getProtectionDomain() {
        return ibv_srq.pd$get(segment());
    }

    public int getHandle() {
        return ibv_srq.handle$get(segment());
    }

    public MemorySegment getMutex() {
        return ibv_srq.mutex$addr(segment());
    }

    public MemorySegment getCond() {
        return ibv_srq.cond$addr(segment());
    }

    public int getEventsCompleted() {
        return ibv_srq.events_completed$get(segment());
    }

    public void setContext(final MemoryAddress value) {
        ibv_srq.context$set(segment(), value);
    }

    public void setSharedReceiveQueueContext(final MemoryAddress value) {
        ibv_srq.srq_context$set(segment(), value);
    }

    public void setProtectionDomain(final MemoryAddress value) {
        ibv_srq.pd$set(segment(), value);
    }

    public void setHandle(final int value) {
        ibv_srq.handle$set(segment(), value);
    }

    public void setEventsCompleted(final int value) {
        ibv_srq.events_completed$set(segment(), value);
    }
}