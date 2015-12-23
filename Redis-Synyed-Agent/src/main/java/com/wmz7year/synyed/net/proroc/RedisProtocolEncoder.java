package com.wmz7year.synyed.net.proroc;

import static com.wmz7year.synyed.constant.RedisProtocolConstant.REDIS_PROTOCOL_CR;
import static com.wmz7year.synyed.constant.RedisProtocolConstant.REDIS_PROTOCOL_LF;

import java.util.Arrays;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoderAdapter;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wmz7year.synyed.entity.RedisCommand;

/**
 * Redis协议编码器<br>
 * 编码方式为String类型的Redis命令结尾添加\r\n结束符<br>
 * 然后转换为mina的IoBuffer对象进入到接下来的处理链
 * 
 * @Title: RedisProtocolEncoder.java
 * @Package com.wmz7year.synyed.net.proroc
 * @author jiangwei (ydswcy513@gmail.com)
 * @date 2015年12月10日 下午4:42:09
 * @version V1.0
 */
public class RedisProtocolEncoder extends ProtocolEncoderAdapter {
	private static final Logger logger = LoggerFactory.getLogger(RedisProtocolEncoder.class);

	/*
	 * @see
	 * org.apache.mina.filter.codec.ProtocolEncoder#encode(org.apache.mina.core.
	 * session.IoSession, java.lang.Object,
	 * org.apache.mina.filter.codec.ProtocolEncoderOutput)
	 */
	@Override
	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
		if (message instanceof RedisCommand) {
			RedisCommand command = (RedisCommand) message;
			byte[] commandData = command.getBytes();
			IoBuffer ioBuffer = IoBuffer.allocate(commandData.length + 2).put(commandData).put(REDIS_PROTOCOL_CR)
					.put(REDIS_PROTOCOL_LF).flip();
			if (logger.isDebugEnabled()) {
				logger.debug("send command:" + new String(commandData) + " hex:" + Arrays.toString(commandData));
			}
			out.write(ioBuffer);
		} else {
			logger.warn("未知类型的数据包 无法编码：" + message);
		}
	}

}
