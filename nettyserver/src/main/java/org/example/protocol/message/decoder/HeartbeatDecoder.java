package org.example.protocol.message.decoder;

import lombok.extern.java.Log;
import org.example.protocol.message.HeartbeatRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 自定义解码器
 *
 * @author pjmike
 * @create 2018-10-24 20:19
 */
@Slf4j
public class HeartbeatDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte version = in.readByte();
        byte command = in.readByte();
        System.out.println(String.format("version : %s, command : %s", version, command));
        HeartbeatRequestPacket requestPacket = new HeartbeatRequestPacket();
        requestPacket.setVersion(version);
        out.add(requestPacket);
    }
}
