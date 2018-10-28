package com.pjmike.netty.protocol.message;

import lombok.Data;

import static com.pjmike.netty.protocol.message.command.Command.HEARTBEAT_REQUEST;

/**
 * @author pjmike
 * @create 2018-10-25 16:12
 */
@Data
public class HeartbeatRequestPacket extends Packet {

    @Override
    public Byte getCommand() {
        return HEARTBEAT_REQUEST;
    }
}
