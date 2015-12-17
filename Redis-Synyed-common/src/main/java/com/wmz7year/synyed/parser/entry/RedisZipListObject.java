package com.wmz7year.synyed.parser.entry;

import static com.wmz7year.synyed.constant.RedisRDBConstant.REDIS_LIST;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.wmz7year.synyed.exception.RedisRDBException;

import static com.wmz7year.synyed.constant.RedisRDBConstant.REDIS_ENCODING_ZIPLIST;

/**
 * redis ziplist类型数据结构对象
 * 
 * @Title: RedisZipListObject.java
 * @Package com.wmz7year.synyed.parser.entry
 * @author jiangwei (ydswcy513@gmail.com)
 * @date 2015年12月17日 上午11:13:13
 * @version V1.0
 */
public class RedisZipListObject extends RedisObject {
	/**
	 * 代表zlist结尾
	 */
	private static final byte ZLEND = (byte) 0xFF;

	private byte[] buffer;

	private ByteArrayInputStream bis = null;
	/**
	 * ziplist中元素的数量
	 */
	private int entryCount;

	/**
	 * ziplist解析出的元素
	 */
	private List<String> elements = new ArrayList<String>();
	/**
	 * 当前元素读取的字节数
	 */
	private int elementReadLength;

	public RedisZipListObject(byte[] buffer) throws RedisRDBException {
		this.buffer = buffer;

		bis = new ByteArrayInputStream(buffer);

		// 检查ziplist的数据完整性
		checkZLBytes();

		// 获取zltail 目前没发现用处
		getZipListZLTail();

		// 获取元素的数量
		this.entryCount = getEntryCount();

		// 处理读取元素内容
		processReadEntries();

		// 校验是否读完了
		if (readByte() != ZLEND) {
			throw new RedisRDBException("ziplist解析错误");
		}
	}

	/**
	 * 校验ziplist数据完整性的方法<br>
	 * 读取4个字节小端法转换为1个int 然后比对数据的长度是否一样
	 * 
	 * @throws RedisRDBException
	 *             当发生问题时抛出该异常
	 */
	private void checkZLBytes() throws RedisRDBException {
		byte[] buf = new byte[4];
		if (!readBytes(buf, 0, 4)) {
			throw new RedisRDBException("解析错误");
		}
		int zlbytes = byte2Int(buf);
		if (zlbytes != buffer.length) {
			throw new RedisRDBException("错误长度的ziplist数据");
		}
	}

	/**
	 * 获取ziplist元素开始位置id方法
	 * 
	 * @return ziplist元素开始位置
	 * @throws RedisRDBException
	 *             出现问题时抛出该异常
	 */
	private int getZipListZLTail() throws RedisRDBException {
		byte[] buf = new byte[4];
		if (!readBytes(buf, 0, 4)) {
			throw new RedisRDBException("解析错误");
		}
		int zlTail = byte2Int(buf);
		return zlTail;
	}

	/**
	 * 获取ziplist中元素数量的方法
	 * 
	 * @return ziplist中元素数量
	 * @throws RedisRDBException
	 *             出现问题时抛出该异常
	 */
	private int getEntryCount() throws RedisRDBException {
		byte[] buf = new byte[4];
		// 只读取两个字节
		if (!readBytes(buf, 0, 2)) {
			throw new RedisRDBException("解析错误");
		}
		int entryCount = byte2Int(buf);
		return entryCount;
	}

	/**
	 * 处理读取ziplist元素内容的方法
	 * 
	 * @throws RedisRDBException
	 *             当读取发生错误时抛出该异常
	 */
	private void processReadEntries() throws RedisRDBException {
		// 循环获取每一个元素
		for (int i = 0; i < entryCount; i++) {
			// 上一个元素的长度
			int perEntryLength = readPerEntryLength();
			if (perEntryLength != 0) { // 如果长度为0则说明是第一个元素
				// -1是为了减去刚刚读取的1位长度
				if ((elementReadLength - 1) != perEntryLength) {
					throw new RedisRDBException("redis元素解析错误");
				} else {
					elementReadLength = 1;
				}
			}
			// 读取符号
			byte entrySpecialFlag = readEntrySpecialFlag();
			// 读取元素长度
			String entry = readEntry(entrySpecialFlag);
			elements.add(entry);
		}
	}

	/**
	 * 读取元素内容的方法<br>
	 * 
	 * <pre>
	 * 	Special flag : This flag indicates whether the entry is a string or an integer. 
	 *  It also indicates the length of the string, or the size of the integer. 
	 *  The various encodings of this flag are shown below :
	 *  |00pppppp| – 1 byte : String value with length less than or equal to 63 bytes (6 bits).
	 *  |01pppppp|qqqqqqqq| – 2 bytes : String value with length less than or equal to 16383 bytes (14 bits).
	 *  |10______|qqqqqqqq|rrrrrrrr|ssssssss|tttttttt| – 5 bytes : String value with length greater than or equal to 16384 bytes.
	 *  |1100____| – Read next 2 bytes as a 16 bit signed integer
	 *  |1101____| – Read next 4 bytes as a 32 bit signed integer
	 *  |1110____| – Read next 8 bytes as a 64 bit signed integer
	 *  |11110000| – Read next 3 bytes as a 24 bit signed integer
	 *  |11111110| – Read next byte as an 8 bit signed integer
	 *  |1111xxxx| – (with xxxx between 0000 and 1101) immediate 4 bit integer. 
	 *  Unsigned integer from 0 to 12. The encoded value is actually from 1 to 13 because 0000 
	 *  and 1111 can not be used, so 1 should be subtracted from the encoded 4 bit value to obtain the right value.
	 * 
	 * </pre>
	 * 
	 * @param readEntrySpecialFlag
	 *            元素长度符号标识位
	 * @return 元素的长度
	 * @throws RedisRDBException
	 *             读取发生错误时抛出该异常
	 */
	private String readEntry(byte readEntrySpecialFlag) throws RedisRDBException {
		byte bit7 = (byte) ((readEntrySpecialFlag >> 7) & 0x1);
		byte bit6 = (byte) ((readEntrySpecialFlag >> 6) & 0x1);
		byte bit5 = (byte) ((readEntrySpecialFlag >> 5) & 0x1);
		byte bit4 = (byte) ((readEntrySpecialFlag >> 4) & 0x1);
		byte bit3 = (byte) ((readEntrySpecialFlag >> 3) & 0x1);
		byte bit2 = (byte) ((readEntrySpecialFlag >> 2) & 0x1);
		byte bit1 = (byte) ((readEntrySpecialFlag >> 1) & 0x1);
		byte bit0 = (byte) ((readEntrySpecialFlag >> 0) & 0x1);

		if (bit7 == 0 && bit6 == 0) { // 6bit字符串
			// TODO
		} else if (bit7 == 1 && bit6 == 1 && bit5 == 1 && bit4 == 1) { // 4bit整数数据
			byte result = (byte) ((bit3 << 3) + (bit2 << 2) + (bit1 << 1) + (bit0 << 0) - 1);
			return String.valueOf(result);
		} else {
			throw new RedisRDBException("不支持的entry special符号");
		}
		return null;
	}

	/**
	 * 读取entry special符号
	 * 
	 * @return special符号
	 * @throws RedisRDBException
	 *             当读取发生错误时抛出该异常
	 */
	private byte readEntrySpecialFlag() throws RedisRDBException {
		elementReadLength++;
		return readByte();
	}

	/**
	 * 读取上一个元素的长度的方法
	 * 
	 * @return 上一个元素的长度 如果返回值为0则说明是第一个元素
	 * @throws RedisRDBException
	 *             当读取发生错误时抛出该异常
	 */
	private int readPerEntryLength() throws RedisRDBException {
		elementReadLength++;
		return readByte();
	}

	/**
	 * 读取1个字节的方法
	 * 
	 * @return 读取到的字节数据
	 * @throws RedisRDBException
	 *             当读取到结尾或者发生错误时抛出该异常
	 */
	private byte readByte() throws RedisRDBException {
		int data = bis.read();
		// 强转成byte
		byte b = (byte) data;
		return b;
	}

	/**
	 * 读取一个byte数组的方法
	 * 
	 * @param buf
	 *            需要读取的byte数组
	 * @param start
	 *            读取的起始位
	 * @param num
	 *            读取数量
	 * @return true为读取成功 false为读取失败
	 * @throws RedisRDBException
	 *             读取过程中可能出现的异常
	 */
	private boolean readBytes(byte[] buf, int start, int num) throws RedisRDBException {
		if (num < 0) {
			throw new RedisRDBException("Num must bigger than zero");
		}
		if (start < 0) {
			throw new RedisRDBException("Start must bigger than zero");
		}
		if (num > buf.length) {
			throw new RedisRDBException("Num must less than buf length");
		}
		int flag = bis.read(buf, start, num);
		return flag == num;
	}

	/**
	 * byte数组转换为int值的方法<br>
	 * 
	 * 
	 * @param buffer
	 *            需要转换的byte数组
	 * @return 转换后的int值
	 * @throws RedisRDBException
	 *             当参数不正确时会抛出该异常
	 */
	private int byte2Int(byte[] buffer) throws RedisRDBException {
		if (buffer == null) {
			throw new NullPointerException();
		}
		if (buffer.length != 4) {
			throw new RedisRDBException("Error buffer length can not cover bytes to int:" + Arrays.toString(buffer));
		}
		return (((buffer[3]) << 24) | ((buffer[2] & 0xff) << 16) | ((buffer[1] & 0xff) << 8) | ((buffer[0] & 0xff)));
	}

	/*
	 * @see com.wmz7year.synyed.parser.entry.RedisObject#getType()
	 */
	@Override
	public byte getType() {
		return REDIS_LIST;
	}

	/*
	 * @see com.wmz7year.synyed.parser.entry.RedisObject#getEncoding()
	 */
	@Override
	public byte getEncoding() {
		return REDIS_ENCODING_ZIPLIST;
	}

	/*
	 * @see com.wmz7year.synyed.parser.entry.RedisObject#toCommand()
	 */
	@Override
	public String toCommand() {
		StringBuilder result = new StringBuilder();
		for (String element : elements) {
			result.append(element).append(' ');
		}
		if (result.length() > 0 && result.charAt(result.length() - 1) == ' ') {
			return result.substring(0, result.length() - 1);
		}
		return result.toString();
	}

	/*
	 * @see com.wmz7year.synyed.parser.entry.RedisObject#getBuffer()
	 */
	@Override
	public byte[] getBuffer() {
		return this.buffer;
	}

	/*
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "RedisZipListObject [buffer length=" + buffer.length + ",command=" + toCommand() + "]";
	}

}
