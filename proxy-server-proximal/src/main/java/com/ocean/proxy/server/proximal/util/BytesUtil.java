package com.ocean.proxy.server.proximal.util;

import java.math.BigDecimal;

/**
 * Description: 字节相关操作的工具类
 * 字节数组的组合、拆分。 字节数组与基本类型(short,int,long,float,double)的互相转换
 *
 * @author: Ocean
 * DateTime: 2022/2/15 11:11
 */
public class BytesUtil {

    /**
     * 按照正序整合多个字节数组
     *
     * @param bytes 多个字节数组
     * @return 整合完成后的整个字节数组
     */
    public static byte[] concatBytes(byte[]... bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("this byteArray must not be null or empty");
        }
        int length = 0;
        for (byte[] bt : bytes) {
            length += bt.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] bt : bytes) {
            System.arraycopy(bt, 0, result, offset, bt.length);
            offset += bt.length;
        }
        return result;
    }

    /**
     * 截取指定长度的字节数组
     *
     * @param bytes  需要进行截取的字节数组
     * @param begin  起始位置
     * @param length 截取长度
     * @return 返回截取后的字节数组
     */
    public static byte[] splitBytes(byte[] bytes, int begin, int length) {
        byte[] result = new byte[length];
        System.arraycopy(bytes, begin, result, 0, length);
        return result;
    }

    public static byte[] splitBytes(byte[] bytes, int begin) {
        int length = bytes.length - begin;
        byte[] result = new byte[length];
        System.arraycopy(bytes, begin, result, 0, length);
        return result;
    }

    /**
     * 向字节数组指定位置插入另外一个字节数组
     *
     * @param source 原字节数组
     * @param insert 需要插入的字节数组
     * @param index  插入位置
     * @return 返回插入的字节数组
     */
    public static byte[] insertBytes(byte[] source, byte[] insert, int index) {
        if (index > source.length) {
            throw new IllegalArgumentException("数据插入位置超出字节数组范围");
        }
        byte[] result = new byte[source.length + insert.length];
        System.arraycopy(source, 0, result, 0, index);
        System.arraycopy(insert, 0, result, index, insert.length);
        System.arraycopy(source, index, result, index + insert.length, source.length - index);
        return result;
    }

    /**
     * 大小端转换
     *
     * @param bytes 需要转换的字节数组
     * @return 转换后的字节数组
     */
    public static byte[] bytesReverseOrder(byte[] bytes) {
        int length = bytes.length;
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[length - i - 1] = bytes[i];
        }
        return result;
    }

    private static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd',
            'e', 'f'};
    private static final char[] DIGITS_UPPER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D',
            'E', 'F'};

    /**
     * 将字节数组转换为16进制字符串，默认大写
     *
     * @param bytes 需要转换的字节数组
     * @return 转换后的16进制字符串
     */
    public static String toHexString(byte[] bytes) {
        return toHexString(bytes, false);
    }

    /**
     * 将字节数组转换为16进制字符串
     *
     * @param bytes       需要转换的字节数组
     * @param toLowerCase 选择大小写，true为小写，false为大写
     * @return 转换后的16进制字符串
     */
    public static String toHexString(byte[] bytes, boolean toLowerCase) {
        char[] toDigits;
        if (toLowerCase) {
            toDigits = DIGITS_LOWER;
        } else {
            toDigits = DIGITS_UPPER;
        }
        final int l = bytes.length;
        final char[] out = new char[l << 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & bytes[i]) >>> 4];
            out[j++] = toDigits[0x0F & bytes[i]];
        }
        return new String(out);
    }

    /**
     * 将16进制字符串转换为字节数组
     *
     * @param hex 转换后的16进制字符串
     * @return 需要转换的字节数组
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.trim().length() == 0) {
            throw new IllegalArgumentException("this byteArray must not be null or empty");
        }
        char[] data = hex.toCharArray();
        final int len = data.length;
        if ((len & 0x01) != 0) {
            throw new IllegalArgumentException("字符串长度必须为偶数");
        }
        final byte[] out = new byte[len >> 1];
        // two characters form the hex value.
        for (int i = 0, j = 0; j < len; i++) {
            int f = toDigit(data[j], j) << 4;
            j++;
            f = f | toDigit(data[j], j);
            j++;
            out[i] = (byte) (f & 0xFF);
        }
        return out;
    }

    private static int toDigit(final char ch, final int index) {
        final int digit = Character.digit(ch, 16);
        if (digit == -1) {
            throw new IllegalArgumentException("包含错误的16进制字符 " + ch + " at index " + index);
        }
        return digit;
    }

//start=========================基本类型数据转字节数组===============================================

    /**
     * 将short转为高字节在前，低字节在后的byte数组
     *
     * @param n short类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesH(short n) {
        byte[] b = new byte[2];
        b[1] = (byte) (n & 0xff);
        b[0] = (byte) (n >> 8 & 0xff);
        return b;
    }

    /**
     * 将short转为低字节在前，高字节在后的byte数组
     *
     * @param n short类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesL(short n) {
        byte[] b = new byte[2];
        b[0] = (byte) (n & 0xff);
        b[1] = (byte) (n >> 8 & 0xff);
        return b;
    }

    /**
     * 将int转为高字节在前，低字节在后的byte数组
     *
     * @param n int类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesH(int n) {
        byte[] b = new byte[4];
        b[3] = (byte) (n & 0xff);
        b[2] = (byte) (n >> 8 & 0xff);
        b[1] = (byte) (n >> 16 & 0xff);
        b[0] = (byte) (n >> 24 & 0xff);
        return b;
    }

    /**
     * 将int转为低字节在前，高字节在后的byte数组
     *
     * @param n int类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesL(int n) {
        byte[] b = new byte[4];
        b[0] = (byte) (n & 0xff);
        b[1] = (byte) (n >> 8 & 0xff);
        b[2] = (byte) (n >> 16 & 0xff);
        b[3] = (byte) (n >> 24 & 0xff);
        return b;
    }

    /**
     * 将long转为高字节在前，低字节在后的byte数组
     *
     * @param n long类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesH(long n) {
        byte[] byteNum = new byte[8];
        for (int i = 0; i < 8; i++) {
            byteNum[7-i] = (byte) ((n >> 8 * i) & 0xff);
        }
        return byteNum;
    }

    /**
     * 将long转为低字节在前，高字节在后的byte数组
     *
     * @param n long类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesL(long n) {
        byte[] byteNum = new byte[8];
        for (int i = 7; i >= 0; i--) {
            byteNum[i] = (byte) ((n >> 8 * i) & 0xff);
        }
        return byteNum;
    }

    /**
     * 将float转为高字节在前，低字节在后的byte数组
     *
     * @param n float类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesH(float n) {
        return toBytesH(Float.floatToRawIntBits(n));
    }

    /**
     * 将float转为低字节在前，高字节在后的byte数组
     *
     * @param n float类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesL(float n) {
        return toBytesL(Float.floatToRawIntBits(n));
    }

    /**
     * 将double转为高字节在前，低字节在后的byte数组
     *
     * @param n double类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesH(double n) {
        return toBytesH(Double.doubleToRawLongBits(n));
    }

    /**
     * 将double转为低字节在前，高字节在后的byte数组
     *
     * @param n double类型的数据
     * @return byte[] 转换后的字节数组
     */
    public static byte[] toBytesL(double n) {
        return toBytesL(Double.doubleToRawLongBits(n));
    }

//end=========================基本类型数据转字节数组===============================================


//start=========================字节数组转基本类型数据===============================================

    /**
     * 高字节数组到无符号数值的解析
     *
     * @param bytes 需要转换的字节数组
     * @return 解析后数值
     */
    public static Number toUnsignedNumberH(byte[] bytes) {
        if (bytes.length > 8 || (bytes.length == 8 && bytes[0] < 0)) {
            throw new IllegalArgumentException("超出数值范围");
        }
        if (bytes.length == 8 && bytes[bytes.length - 1] < 0) {
            long lowValue = toNumberH(bytes) & 0x7fffffffffffffffL;
            return BigDecimal.valueOf(lowValue).add(BigDecimal.valueOf(Long.MAX_VALUE)).add(BigDecimal.valueOf(1));
        }
        long num = 0;
        for (int i = 0; i < bytes.length; i++) {
            num <<= 8;
            num |= (bytes[i] & 0xff);
        }
        return num;
    }

    /**
     * 高字节数组到有符号数值的解析
     *
     * @param bytes 需要转换的字节数组
     * @return 解析后数值
     */
    public static long toNumberH(byte[] bytes) {
        if (bytes.length > 8) {
            throw new IllegalArgumentException("字节数组长度错误,length:"+bytes.length);
        }
        long num = 0;
        for (int i = 0; i < bytes.length; ++i) {
            num <<= 8;
            if (i == 0) {
                num |= bytes[i];
            } else {
                num |= (bytes[i] & 0xff);
            }
        }
        return num;
    }

    /**
     * 低字节数组到无符号数值的解析
     *
     * @param bytes 需要转换的字节数组
     * @return 解析后数值
     */
    public static Number toUnsignedNumberL(byte[] bytes) {
        if (bytes.length > 8) {
            throw new IllegalArgumentException("超出数值范围");
        }
        if (bytes.length == 8 && bytes[bytes.length - 1] < 0) {
            long lowValue = toNumberL(bytes) & 0x7fffffffffffffffL;
            return BigDecimal.valueOf(lowValue).add(BigDecimal.valueOf(Long.MAX_VALUE)).add(BigDecimal.valueOf(1));
        }
        long num = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            num <<= 8;
            num |= (bytes[i] & 0xff);
        }
        return num;
    }

    /**
     * 低字节数组到有符号数值的解析
     *
     * @param bytes 需要转换的字节数组
     * @return 解析后数值
     */
    public static long toNumberL(byte[] bytes) {
        if (bytes.length > 8) {
            throw new IllegalArgumentException("字节数组长度错误,length:"+bytes.length);
        }
        long num = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            num <<= 8;
            if (i == bytes.length - 1) {
                num |= bytes[i];
            } else {
                num |= (bytes[i] & 0xff);
            }
        }
        return num;
    }


    /**
     * 高字节数组转换为float
     *
     * @param b 需要转换的字节数组
     * @return float类型的数据
     */
    public static float toFloatH(byte[] b) {
        if (b.length != 4) {
            throw new IllegalArgumentException("字节数组长度错误,限制为4字节，当前length:"+b.length);
        }
        int value = (int)toNumberH(b);
        return Float.intBitsToFloat(value);
    }

    /**
     * 低字节数组转换为float
     *
     * @param b 需要转换的字节数组
     * @return float类型的数据
     */
    public static float toFloatL(byte[] b) {
        if (b.length != 4) {
            throw new IllegalArgumentException("字节数组长度错误,限制为4字节，当前length:"+b.length);
        }
        int value = (int)toNumberL(b);
        return Float.intBitsToFloat(value);
    }

    /**
     * 高字节数组转换为double
     *
     * @param b 需要转换的字节数组
     * @return double类型的数据
     */
    public static double toDoubleH(byte[] b) {
        if (b.length != 8) {
            throw new IllegalArgumentException("字节数组长度错误,限制为8字节，当前length:"+b.length);
        }
        long value = toNumberH(b);
        return Double.longBitsToDouble(value);
    }


    /**
     * 低字节数组转换为double
     *
     * @param b 需要转换的字节数组
     * @return double类型的数据
     */
    public static double toDoubleL(byte[] b) {
        if (b.length != 8) {
            throw new IllegalArgumentException("字节数组长度错误,限制为8字节，当前length:"+b.length);
        }
        long value = toNumberL(b);
        return Double.longBitsToDouble(value);
    }

//end=========================字节数组转基本类型数据===============================================

}
