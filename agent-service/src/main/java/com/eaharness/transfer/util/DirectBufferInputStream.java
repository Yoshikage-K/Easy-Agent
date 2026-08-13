package com.eaharness.transfer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/** Streams one remote part through direct memory without buffering the full part on the heap. */
public class DirectBufferInputStream extends InputStream {
    private final ReadableByteChannel source;
    private final ByteBuffer buffer;
    private boolean eof;

    public DirectBufferInputStream(InputStream source, int bufferSize) {
        this.source = Channels.newChannel(source);
        this.buffer = ByteBuffer.allocateDirect(bufferSize);
        this.buffer.limit(0);
    }

    @Override
    public int read() throws IOException {
        if (!ensureData()) return -1;
        return buffer.get() & 0xff;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (!ensureData()) return -1;
        int count = Math.min(length, buffer.remaining());
        buffer.get(target, offset, count);
        return count;
    }

    private boolean ensureData() throws IOException {
        if (buffer.hasRemaining()) return true;
        if (eof) return false;
        buffer.clear();
        while (true) {
            int count = source.read(buffer);
            if (count < 0) {
                eof = true;
                buffer.limit(0);
                return false;
            }
            if (count > 0) {
                buffer.flip();
                return true;
            }
            buffer.clear();
        }
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
