package org.example.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.example.protocol.message.HeartbeatResponsePacket;
import org.example.protocol.protobuf.MessageBase;

import java.util.UUID;

/**
 * @author pjmike
 * @create 2018-10-24 15:43
 */
@Slf4j
@ChannelHandler.Sharable
public class NettyServerHandler extends SimpleChannelInboundHandler<MessageBase.Message> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageBase.Message msg) throws Exception {
        if (msg.getCmd().equals(MessageBase.Message.CommandType.HEARTBEAT_REQUEST)) {
            System.out.println(String.format("收到客户端发来的心跳消息：%s", msg.toString()));
            //回应pong
            MessageBase.Message pong = new MessageBase.Message().toBuilder().setCmd(MessageBase.Message.CommandType.HEARTBEAT_RESPONSE)
                    .setRequestId(msg.getRequestId())
                    .setContent("pong").build();
            ctx.writeAndFlush(pong);
        } else if (msg.getCmd().equals(MessageBase.Message.CommandType.NORMAL)) {
            System.out.println(String.format("收到客户端的业务消息：%s",msg.toString()));
            MessageBase.Message pong = new MessageBase.Message().toBuilder().setCmd(MessageBase.Message.CommandType.NORMAL)
                    .setRequestId(msg.getRequestId())
                    .setContent("ok").build();
            ctx.writeAndFlush(pong);
        }
    }
}
