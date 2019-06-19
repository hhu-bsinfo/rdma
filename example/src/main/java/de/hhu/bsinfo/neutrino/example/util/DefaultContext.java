package de.hhu.bsinfo.neutrino.example.util;

import de.hhu.bsinfo.neutrino.buffer.RegisteredBuffer;
import de.hhu.bsinfo.neutrino.verbs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

public class DefaultContext extends BaseContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultContext.class);

    private final RegisteredBuffer buffer;
    private final CompletionQueue completionQueue;
    private final CompletionChannel completionChannel;

    private final QueuePair queuePair;

    private final PortAttributes port;

    public DefaultContext(int deviceNumber, int queueSize, long messageSize) throws IOException {
        super(deviceNumber);

        port = getContext().queryPort(1);
        if(port == null) {
            throw new IOException("Unable to query port");
        }

        buffer = getProtectionDomain().allocateMemory(messageSize, AccessFlag.LOCAL_WRITE);
        if(buffer == null) {
            throw new IOException("Unable to allocate message buffer");
        }

        LOGGER.info("Allocated registered memory buffer");

        completionChannel = getContext().createCompletionChannel();
        if(completionChannel == null) {
            throw new IOException("Unable to create completion channel");
        }

        LOGGER.info("Created completion channel");

        completionQueue = getContext().createCompletionQueue(queueSize, completionChannel);
        if(completionQueue == null) {
            throw new IOException("Unable to create completion queue");
        }

        LOGGER.info("Created completion queue");

        queuePair = getProtectionDomain().createQueuePair(new QueuePair.InitialAttributes.Builder(
                QueuePair.Type.RC, completionQueue, completionQueue, queueSize, queueSize, 1, 1).build());
        if(queuePair == null) {
            throw new IOException("Unable to create queue pair");
        }

        LOGGER.info("Created queue pair");

        if(!queuePair.modify(QueuePair.Attributes.Builder.buildInitAttributesRC((short) 0, (byte) 1, AccessFlag.LOCAL_WRITE))) {
            throw new IOException(("Unable to move queue pair into INIT state"));
        }

        LOGGER.info("Moved queue pair into INIT state");
    }

    public DefaultContext(int queueSize, long bufferSize) throws IOException {
        this(0, queueSize, bufferSize);
    }

    public void connect(Socket socket) throws IOException {
        var localInfo = new ConnectionInformation((byte) 1, port.getLocalId(), queuePair.getQueuePairNumber());

        LOGGER.info("Local connection information: {}", localInfo);

        socket.getOutputStream().write(ByteBuffer.allocate(Byte.BYTES + Short.BYTES + Integer.BYTES)
                .put(localInfo.getPortNumber())
                .putShort(localInfo.getLocalId())
                .putInt(localInfo.getQueuePairNumber())
                .array());

        LOGGER.info("Waiting for remote connection information");

        var byteBuffer = ByteBuffer.wrap(socket.getInputStream().readNBytes(Byte.BYTES + Short.BYTES + Integer.BYTES));
        var remoteInfo = new ConnectionInformation(byteBuffer);

        LOGGER.info("Received connection information: {}", remoteInfo);

        if(!queuePair.modify(QueuePair.Attributes.Builder.buildReadyToReceiveAttributesRC(
                remoteInfo.getQueuePairNumber(), remoteInfo.getLocalId(), remoteInfo.getPortNumber()))) {
            throw new IOException("Unable to move queue pair into RTR state");
        }

        LOGGER.info("Moved queue pair into RTR state");

        if(!queuePair.modify(QueuePair.Attributes.Builder.buildReadyToSendAttributesRC())) {
            throw new IOException("Unable to move queue pair into RTS state");
        }

        LOGGER.info("Moved queue pair into RTS state");
    }

    public RegisteredBuffer getBuffer() {
        return buffer;
    }

    public CompletionQueue getCompletionQueue() {
        return completionQueue;
    }

    public CompletionChannel getCompletionChannel() {
        return completionChannel;
    }

    public QueuePair getQueuePair() {
        return queuePair;
    }

    @Override
    public void close() throws Exception {
        queuePair.close();
        completionQueue.close();
        completionChannel.close();
        buffer.close();
        super.close();

        LOGGER.info("Closed context");
    }

    public static final class ConnectionInformation {

        private final byte portNumber;
        private final short localId;
        private final int queuePairNumber;

        ConnectionInformation(byte portNumber, short localId, int queuePairNumber) {
            this.portNumber = portNumber;
            this.localId = localId;
            this.queuePairNumber = queuePairNumber;
        }

        ConnectionInformation(ByteBuffer buffer) {
            portNumber = buffer.get();
            localId = buffer.getShort();
            queuePairNumber = buffer.getInt();
        }

        byte getPortNumber() {
            return portNumber;
        }

        short getLocalId() {
            return localId;
        }

        int getQueuePairNumber() {
            return queuePairNumber;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ConnectionInformation.class.getSimpleName() + "[", "]")
                    .add("portNumber=" + portNumber)
                    .add("localId=" + localId)
                    .add("queuePairNumber=" + queuePairNumber)
                    .toString();
        }
    }
}
