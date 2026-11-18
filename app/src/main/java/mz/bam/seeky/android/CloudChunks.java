package mz.bam.seeky.android;

import java.io.*;
import java.util.Arrays;

/** One fixed allocation for a full block; no growing stream and full-size copy. */
final class CloudChunks {
    static byte[] read(InputStream in, int size) throws IOException {
        if (size < 1 || size > 8388608) throw new IllegalArgumentException("Invalid block size");
        int first = in.read();
        if (first == -1) return new byte[0];
        byte[] block = new byte[size];
        block[0] = (byte) first;
        int offset = 1;
        while (offset < size) {
            int count = in.read(block, offset, size - offset);
            if (count == -1) break;
            if (count == 0) {
                int next = in.read();
                if (next == -1) break;
                block[offset++] = (byte) next;
            } else offset += count;
        }
        return offset == size ? block : Arrays.copyOf(block, offset);
    }
    private CloudChunks() {}
}
