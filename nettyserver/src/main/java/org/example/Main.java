package org.example;

import org.example.server.NettyServer;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Hello world!");
        new NettyServer().start();
        while (true){
            Thread.sleep(1000);
        }
    }
}