package de.hhu.bsinfo.neutrino.api.network.impl.buffer;

import de.hhu.bsinfo.neutrino.api.network.impl.accessor.ScatterGatherAccessor;
import de.hhu.bsinfo.neutrino.api.network.impl.accessor.SendRequestAccessor;
import de.hhu.bsinfo.neutrino.util.MemoryAlignment;
import de.hhu.bsinfo.neutrino.util.MemoryUtil;
import org.agrona.BitUtil;
import org.agrona.UnsafeAccess;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.MessageHandler;

import static org.agrona.BitUtil.align;
import static org.agrona.concurrent.broadcast.RecordDescriptor.PADDING_MSG_TYPE_ID;
import static org.agrona.concurrent.ringbuffer.RecordDescriptor.*;
import static org.agrona.concurrent.ringbuffer.RingBuffer.INSUFFICIENT_CAPACITY;
import static org.agrona.concurrent.ringbuffer.RingBufferDescriptor.*;

/**
 * A ring buffer used for storing requests.
 * This implementation is a modified version of {@link org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer}.
 */
public class RequestBuffer {

    private static final int REQUEST_MESSAGE_ID = 1;

    /**
     * This buffer's maximum capacity in bytes.
     */
    private final int capacity;

    /**
     * The index within our backing buffer at which the head position is stored.
     */
    private final int headPositionIndex;

    /**
     * The index within our backing buffer at which the cached head position is stored.
     */
    private final int headCachePositionIndex;

    /**
     * The index within our backing buffer at which the tail position is stored.
     */
    private final int tailPositionIndex;

    /**
     * The underlying buffer used for storing data.
     */
    private final AtomicBuffer buffer;

    /**
     * Bitmask used to keep indices within the buffer's bounds.
     */
    private final int indexMask;

    public RequestBuffer(int size) {

        // Allocate a new page-aligned buffer
        buffer = MemoryUtil.allocateAligned(size + TRAILER_LENGTH, MemoryAlignment.PAGE);

        // Store the buffer's actual capacity
        capacity = buffer.capacity() - TRAILER_LENGTH;
        indexMask = capacity - 1;

        // Verify the buffer is correctly aligned
        buffer.verifyAlignment();

        // Remember positions at which indices are stored
        headPositionIndex = capacity + HEAD_POSITION_OFFSET;
        headCachePositionIndex = capacity + HEAD_CACHE_POSITION_OFFSET;
        tailPositionIndex = capacity + TAIL_POSITION_OFFSET;
    }

    public int read(final MessageHandler handler, final int limit) {

        // Keep track of the messages we already read
        var messagesRead = 0;

        // Retrieve our current position within the buffer
        final var buffer = this.buffer;
        final var headPositionIndex = this.headPositionIndex;
        final var head = buffer.getLong(headPositionIndex);
        final var capacity = this.capacity;
        final var headIndex = (int) head & indexMask;
        final var maxBlockLength = capacity - headIndex;

        // Keep track of the number of bytes we read
        var bytesRead = 0;
        while ((bytesRead < maxBlockLength) && (messagesRead < limit)) {
            final var recordIndex = headIndex + bytesRead;
            final var recordLength = buffer.getIntVolatile(lengthOffset(recordIndex));

            // If this record wasn't commited yet, we have to abort
            if (recordLength <= 0) {
                break;
            }

            // Increment the number of bytes processed
            bytesRead += align(recordLength, ALIGNMENT);

            // Skip this record if it represents padding
            final var messageTypeId = buffer.getInt(typeOffset(recordIndex));
            if (messageTypeId == PADDING_MSG_TYPE_ID) {
                continue;
            }

            handler.onMessage(messageTypeId, buffer, recordIndex + HEADER_LENGTH, recordLength - HEADER_LENGTH);
            messagesRead++;
        }

        // Return the number of bytes read so the consumer can commit it later
        return bytesRead;
    }

    public void commitRead(int bytes) {
        final var buffer = this.buffer;
        final var headPositionIndex = this.headPositionIndex;
        final var head = buffer.getLong(headPositionIndex);
        final var headIndex = (int) head & indexMask;

        buffer.setMemory(headIndex, bytes, (byte) 0);
        buffer.putLongOrdered(headPositionIndex, head + bytes);
    }

    public int tryClaim(final int scatterGatherElements) {

        final var buffer = this.buffer;

        // Calculate the required size in bytes
        final var recordLength = SendRequestAccessor.ELEMENT_SIZE +
                ScatterGatherAccessor.ELEMENT_SIZE * scatterGatherElements +
                HEADER_LENGTH;

        // Claim the required space
        final var recordIndex = claim(buffer, recordLength);

        // Check if space was claimed sucessfully
        if (recordIndex == INSUFFICIENT_CAPACITY) {
            return INSUFFICIENT_CAPACITY;
        }

        // Block claimed space
        buffer.putIntOrdered(lengthOffset(recordIndex), -recordLength);
        UnsafeAccess.UNSAFE.storeFence();
        buffer.putInt(typeOffset(recordIndex), REQUEST_MESSAGE_ID);

        // Return the index at which the producer may write its request
        return encodedMsgOffset(recordIndex);
    }

    public void commitWrite(final int index) {

        final var buffer = this.buffer;

        // Calculate the request index and length
        final int recordIndex = index - HEADER_LENGTH;
        final int recordLength = buffer.getInt(lengthOffset(recordIndex));

        // Commit the request
        buffer.putIntOrdered(lengthOffset(recordIndex), -recordLength);
    }

    private int claim(final AtomicBuffer buffer, final int length) {

        // Calculate the required space to claim
        final var required = BitUtil.align(length, ALIGNMENT);

        // This buffer's capacity
        final var total = capacity;

        // The index at which the tail position is stored
        final var tailPosition = tailPositionIndex;

        // The index at which the cached head position is stored
        final var headCachePosition = headCachePositionIndex;

        // Mask used to keep indices within bounds
        final var mask = indexMask;

        var head = buffer.getLongVolatile(headCachePosition);
        long tail;
        int tailIndex;
        int padding;

        do {

            // Calculate available space using the cached head position
            tail = buffer.getLongVolatile(tailPosition);
            final var available = total - (int) (tail - head);
            if (required > available) { // If the required size is less than the cached available space left

                // Calculate available space using the head position
                head = buffer.getLongVolatile(headPositionIndex);
                if (required > (total - (int) (tail - head))) { // If the required size is less than the current available space left
                    return INSUFFICIENT_CAPACITY;
                }

                // Update the cached head position
                buffer.putLongOrdered(headCachePosition, head);
            }

            // At this point we know that there is a chunk of
            // memory at least the size we requested

            // Try to acquire the required space
            padding = 0;
            tailIndex = (int) tail & mask;
            final var remaining = total - tailIndex;
            if (required > remaining) { // If the space between the tail and the upper bound is not sufficient

                // Wrap around the head index
                var headIndex = (int) head & mask;
                if (required > headIndex) {  // If there is not enough space at the beginning of our buffer

                    // Update our head index for one last try
                    head = buffer.getLongVolatile(headPositionIndex);
                    headIndex = (int) head & mask;
                    if (required > headIndex) {
                        return INSUFFICIENT_CAPACITY;
                    }

                    // Update the cached head position
                    buffer.putLongOrdered(headCachePosition, head);
                }

                padding = remaining;
            }

        } while(!buffer.compareAndSetLong(tailPosition, tail, tail + required + padding));

        if (padding != 0) {
            buffer.putIntOrdered(lengthOffset(tailIndex), -padding);
            UnsafeAccess.UNSAFE.storeFence();

            buffer.putInt(typeOffset(tailIndex), PADDING_MSG_TYPE_ID);
            buffer.putIntOrdered(lengthOffset(tailIndex), padding);

            // If there was padding at the end of the buffer
            // our claimed space starts at index 0
            tailIndex = 0;
        }

        return tailIndex;
    }

    public int size() {

        final var buffer = this.buffer;
        final var headPositionIndex = this.headPositionIndex;
        final var tailPositionIndex = this.tailPositionIndex;

        long headBefore;
        long tail;
        long headAfter = buffer.getLongVolatile(headPositionIndex);

        do {
            headBefore = headAfter;
            tail = buffer.getLongVolatile(tailPositionIndex);
            headAfter = buffer.getLongVolatile(headPositionIndex);
        } while (headAfter != headBefore);

        return (int) (tail - headAfter);
    }

    public long memoryAddress() {
        return buffer.addressOffset();
    }
}
