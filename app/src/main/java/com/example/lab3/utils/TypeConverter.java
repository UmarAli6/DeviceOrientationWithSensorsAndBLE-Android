package com.example.lab3.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Code was provided by teacher Anders Lindström.
 */
public class TypeConverter {

    /**
     * Convert <em>four</em> bytes to an int.
     *
     * @param bytes  an array with bytes, of length four or greater
     * @param offset Index of the first byte in the sequence of four.
     * @return The (Java) int corresponding to the four bytes.
     */
    public static int fourBytesToInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * Convert <em>four</em> bytes to a float.
     *
     * @param bytes  an array with bytes, of length four or greater
     * @param offset Index of the first byte in the sequence of four.
     * @return The (Java) float corresponding to the four bytes.
     */
    public static float fourBytesToFloat(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    /**
     * Create a an array of bytes representing a Movesense 2.0 command string, ASCII encoded..
     * The first byte is always set to 1.
     *
     * @param id      The id used to identify this command, and incoming data from sensor.
     * @param command The command, see http://www.movesense.com/docs/esw/api_reference/.
     * @return An array of bytes representing a Movesense 2.0 command string.
     */
    public static byte[] stringToAsciiArray(byte id, String command) {
        if (id > 127) throw new IllegalArgumentException("id= " + id);
        char[] chars = command.trim().toCharArray();
        byte[] ascii = new byte[chars.length + 2];
        ascii[0] = 1;
        ascii[1] = id;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] > 127) throw new IllegalArgumentException("ascii val= " + (int) chars[i]);
            ascii[i + 2] = (byte) chars[i];
        }
        return ascii;
    }

    public static byte[] stringToAsciiArray(String str) {
        char[] chars = str.trim().toCharArray();
        byte[] ascii = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] > 127) throw new IllegalArgumentException("ascii val= " + (int) chars[i]);
            ascii[i] = (byte) chars[i];
        }
        return ascii;
    }
}
