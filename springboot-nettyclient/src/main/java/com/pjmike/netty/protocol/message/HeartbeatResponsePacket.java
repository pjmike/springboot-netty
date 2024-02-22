package com.pjmike.netty.protocol.message;

import com.pjmike.netty.protocol.message.command.Command;

/**
 * @author pjmike
 * @create 2018-10-25 16:16
 */
public class HeartbeatResponsePacket extends Packet {

    @Override
    public Byte getCommand() {
        return Command.HEARTBEAT_RESPONSE;
    }
}
