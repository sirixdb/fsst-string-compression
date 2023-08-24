package fsst;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Utils {
    static int boolToInt(boolean value) {
        return value ? 1 : 0;
    }

    public static long fsst_unaligned_load(byte[] v, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(v, offset, 8);
        buffer.order(ByteOrder.nativeOrder()); // Assuming you want native byte order
        return buffer.getLong();
    }

}
