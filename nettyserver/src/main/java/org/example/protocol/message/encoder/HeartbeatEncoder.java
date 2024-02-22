package org.example.protocol.message.encoder;

import org.example.protocol.message.HeartbeatRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 自定义编码器
 *
 * @author pjmike
 * @create 2018-10-24 20:14
 */
public class HeartbeatEncoder extends MessageToByteEncoder<HeartbeatRequestPacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, HeartbeatRequestPacket msg, ByteBuf out) throws Exception {
        out.writeByte(msg.getVersion());
        out.writeByte(msg.getCommand());
    }
}
