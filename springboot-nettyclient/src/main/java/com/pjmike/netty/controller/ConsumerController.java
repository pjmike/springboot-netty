package com.pjmike.netty.controller;

import com.pjmike.netty.client.NettyClient;
import com.pjmike.netty.protocol.protobuf.MessageBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @author pjmike
 * @create 2018-10-24 16:47
 */
@Slf4j
@RestController
public class ConsumerController {
    @Autowired
    private NettyClient nettyClient;

    @GetMapping("/send")
    public String send() {
        MessageBase.Message message = new MessageBase.Message()
                .toBuilder().setCmd(MessageBase.Message.CommandType.NORMAL)
                .setContent("hello netty")
                .setRequestId(UUID.randomUUID().toString()).build();
        log.info("发送业务消息");
        nettyClient.sendMsg(message);
        return "send ok";
    }
}
