## 前言
这一篇文章主要介绍如何用Springboot 整合 Netty,由于本人尚处于学习Netty的过程中，并没有将Netty 运用到实际生产项目的经验，这里也是在网上搜寻了一些Netty例子学习后总结来的，借鉴了他人的写法和经验。如有重复部分，还请见谅。



关于SpringBoot 如何整合使用 Netty ,我将分为以下几步进行分析与讨论：
- 构建Netty 服务端
- 构建Netty 客户端
- 利用protobuf定义消息格式
- 服务端空闲检测
- 客户端发送心跳包与断线重连

PS: 我这里为了简单起见（主要是懒），将 Netty 服务端与客户端放在了同一个SpringBoot工程里，当然也可以将客户端和服务端分开。


## 构建 Netty 服务端
Netty 服务端的代码其实比较简单，代码如下：
```java
@Component
@Slf4j
public class NettyServer {
    /**
     * boss 线程组用于处理连接工作
     */
    private EventLoopGroup boss = new NioEventLoopGroup();
    /**
     * work 线程组用于数据处理
     */
    private EventLoopGroup work = new NioEventLoopGroup();
    @Value("${netty.port}")
    private Integer port;
    /**
     * 启动Netty Server
     *
     * @throws InterruptedException
     */
    @PostConstruct
    public void start() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, work)
                // 指定Channel
                .channel(NioServerSocketChannel.class)
                //使用指定的端口设置套接字地址
                .localAddress(new InetSocketAddress(port))

                //服务端可连接队列数,对应TCP/IP协议listen函数中backlog参数
                .option(ChannelOption.SO_BACKLOG, 1024)

                //设置TCP长连接,一般如果两个小时内没有数据的通信时,TCP会自动发送一个活动探测数据报文
                .childOption(ChannelOption.SO_KEEPALIVE, true)

                //将小的数据包包装成更大的帧进行传送，提高网络的负载,即TCP延迟传输
                .childOption(ChannelOption.TCP_NODELAY, true)

                .childHandler(new NettyServerHandlerInitializer());
        ChannelFuture future = bootstrap.bind().sync();
        if (future.isSuccess()) {
            log.info("启动 Netty Server");
        }
    }

    @PreDestroy
    public void destory() throws InterruptedException {
        boss.shutdownGracefully().sync();
        work.shutdownGracefully().sync();
        log.info("关闭Netty");
    }
}

```
因为我们在springboot 项目中使用 Netty ,所以我们将Netty 服务器的启动封装在一个 `start()`方法，并使用 `@PostConstruct`注解，在指定的方法上加上 `@PostConstruct`注解来表示该方法在 Spring 初始化 `NettyServer`类后调用。

考虑到使用心跳机制等操作，关于ChannelHandler逻辑处理链的部分将在后面进行阐述。

## 构建 Netty 客户端
Netty 客户端代码与服务端类似，代码如下：
```java
@Component
@Slf4j
public class NettyClient  {
    private EventLoopGroup group = new NioEventLoopGroup();
    @Value("${netty.port}")
    private int port;
    @Value("${netty.host}")
    private String host;
    private SocketChannel socketChannel;

    public void sendMsg(MessageBase.Message message) {
        socketChannel.writeAndFlush(message);
    }

    @PostConstruct
    public void start()  {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .remoteAddress(host, port)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ClientHandlerInitilizer());
        ChannelFuture future = bootstrap.connect();
        //客户端断线重连逻辑
        future.addListener((ChannelFutureListener) future1 -> {
            if (future1.isSuccess()) {
                log.info("连接Netty服务端成功");
            } else {
                log.info("连接失败，进行断线重连");
                future1.channel().eventLoop().schedule(() -> start(), 20, TimeUnit.SECONDS);
            }
        });
        socketChannel = (SocketChannel) future.channel();
    }
}
```
上面还包含了客户端断线重连的逻辑，更多细节问题，将在下面进行阐述。

## 使用 protobuf 构建通信协议
在整合使用 Netty 的过程中，我们使用 Google 的protobuf定义消息格式，下面来简单介绍下 protobuf
### protobuf简介
Google 官方给 protobuf的定义如下：
> Protocol Buffers 是一种轻便高效的结构化数据存储格式，可以用于结构化数据序列化，很适合做数据存储或 RPC 数据交换格式。它可用于通讯协议、数据存储等领域的语言无关、平台无关、可扩展的序列化结构数据格式。

在 Netty 中常用 protobuf 来做序列化方案，当然也可以用 protobuf来构建 客户端与服务端之间的通信协议

### 为什么要用protobuf
我们这里是用 protobuf 做为我们的序列化手段，那我们为什么要使用 protobuf,而不使用其他序列化方案呢，比如 jdk 自带的序列化，Thrift,fastjson等。

首先 jdk 自带序列化手段有很多缺点，比如：
- 序列化后的码流太大
- 性能太低
- 无法跨语言

而 Google Protobuf 跨语言，支持C++、java和python。然后利用protobuf 编码后的消息更小，有利于存储和传输，并且其性能也非常高，相比其他序列化框架，它也是非常有优势的，具体的关于Java 各种序列化框架比较此处就不多说了。总之，目前Google Protobuf 广泛的被使用到各种项目，它的诸多优点让我们选择使用它。


### 怎么使用protobuf
对于 Java 而言，使用 protobuf 主要有以下几步：
- 在 `.proto` 文件中定义消息格式
- 使用 protobuf 编译器编译 `.proto`文件 成 Java 类
- 使用 Java 对应的 protobuf API来写或读消息


#### 定义 protobuf 协议格式
这里为我Demo里的 `message.proto`文件为例，如下：
```java
//protobuf语法有 proto2和proto3两种，这里指定 proto3
syntax = "proto3"; 
// 文件选项
option java_package = "com.pjmike.server.protocol.protobuf";
option java_outer_classname = "MessageBase";
// 消息模型定义
message Message {
    string requestId = 1;
    CommandType cmd = 2;
    string content = 3;
    enum CommandType {
        NORMAL = 0; //常规业务消息
        HEARTBEAT_REQUEST = 1; //客户端心跳消息
        HEARTBEAT_RESPONSE = 2; //服务端心跳消息
    }
}
```
文件解读：
- 文中的第一行指定正在使用 `proto3`语法，如果没有指定，编译器默认使用 `proto2`的语法。现在新项目中可能一般多用 `proto3`的语法，`proto3`比 `proto2`支持更多的语言但更简洁。如果首次使用 protobuf,可以选择使用 `proto3`
- 定义 `.proto`文件时，可以标注一系列的选项，一些选项是文件级别的，比如上面的第二行和第三行，`java_package`文件选项表明protocol编译器编译 `.proto`文件生成的 Java 类所在的包，`java_outer_classname`选项表明想要生成的 Java 类的名称
- `Message `中定义了具体的消息格式，我这里定义了三个字段，每个字段都有唯一的一个数字标识符，这些标识符用来在消息的二进制格式中识别各个字段的
- `Message`中还添加了一个枚举类型，该枚举中含有类型 `CommandType `中所有的值，每个枚举类型必须将其第一个类型映射为 0，该0值为默认值。

**消息模型定义**

关于消息格式，此处我只是非常非常简单的定义了几个字段，`requestId`代表消息Id,`CommandType`表示消息的类型，这里简单分为心跳消息类型和业务消息类型，然后`content`就是具体的消息内容。这里的消息格式定义是十分简陋，真正的项目实战中，关于自定义消息格式的要求是非常多的，是比较复杂的。

上面简单的介绍了 protobuf的一些语法规则，关于 protobuf语法的更多介绍参考官方文档：https://developers.google.com/protocol-buffers/docs/proto3
#### 使用 `.proto`编译器编译
第一步已经定义好了 protobuf的消息格式，然后我们用 `.proto`文件的编译器将我们定义的 消息格式编译生成对应的 Java类，以便于我们在项目中使用该消息类。

关于protobuf编译器的安装这里我就不细说，详情见官方文档： https://developers.google.com/protocol-buffers/

安装好编译器以后，使用以下命令编译`.proto`文件：
```
protoc -I = ./ --java_out=./ ./Message.proto
```
- `-I` 选项用于指定待编译的 `.proto`消息定义文件所在的目录，该选项也可以写作为 `--proto_path`
- `--java_out`选项表示生成 Java代码后存放位置，对于不同语言，我们的选项可能不同，比如生成C++代码为 `--cpp_out`
- 在前两个选项后再加上 待编译的消息定义文件

#### 使用 Java 对应 的 protobuf API来读写消息
前面已经根据 `.proto`消息定义文件生成的Java类，我们这里代码根据 `Message.proto`生成了`MessageBase`类，但是要正常的使用生成的 Java 类，我们还需要引入 protobuf-java的依赖：
```xml
<dependency>
    <groupId>com.google.protobuf</groupId>
    <artifactId>protobuf-java</artifactId>
    <version>3.5.1</version>
</dependency>
```
使用 protobuf 生成的每一个 Java类中，都会包含两种内部类：Msg 和 Msg 包含的 Builder(这里的Msg就是实际消息传输类)。具体是`.proto`中定义的每一个message 都会生成一个 Msg，每一个Msg对应一个 Builder:
- Buidler提供了构建类，查询类的API
- Msg提供了查询，序列化，反序列化的API

比如我们使用 Builder来构建 Msg,例子如下：
```java
public class MessageBaseTest {
    public static void main(String[] args) {
        MessageBase.Message message = MessageBase.Message.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setContent("hello world").build();
        System.out.println("message: "+message.toString());
    }
}
```
这里就不多介绍protobuf-java API的相关用法了，更多详情还是参考官方文档：https://developers.google.com/protocol-buffers/docs/reference/java/

### protobuf的编解码器
上面说了这么多，消息传输格式已经定义好了，但是在客户端和服务端传输过程中我们还需要对这种 protobuf格式进行编解码，当然我们可以自定义消息的编解码，`protobuf-java` 的API中提供了相关的序列化和反序列化方法。好消息是，Netty 为了支持 protobuf提供了针对 protobuf的编解码器，如下表所示（摘自《Netty实战》) ：

名称 | 描述
---|---
ProtobufDecoder | 使用 protobuf 对消息进行解码
ProtobufEncoder | 使用 protobuf 对消息进行编码
ProtobufVarint32FrameDecoder| 根据消息中的 Google Protocol Buffers 的 “Base 128 Varint" 整型长度字段值动态地分割所接收到的 ByteBuf
ProtobufVarint32LengthFieldPrepender | 向 ByteBuf 前追加一个Google Protocol Buffers 的 “Base 128 Varint" 整型长度字段值

有了这些编解码器，将其加入客户端和服务端的 ChannelPipeline中以用于对消息进行编解码，如下：
```java
public class NettyServerHandlerInitializer extends ChannelInitializer<Channel> {

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ch.pipeline()
                //空闲检测
                .addLast(new ServerIdleStateHandler())
                .addLast(new ProtobufVarint32FrameDecoder())
                .addLast(new ProtobufDecoder(MessageBase.Message.getDefaultInstance()))
                .addLast(new ProtobufVarint32LengthFieldPrepender())
                .addLast(new ProtobufEncoder())
                .addLast(new NettyServerHandler());
    }
}
```
## 客户端心跳机制
### 心跳机制简介
心跳是在TCP长连接中，客户端与服务端之间定期发送的一种特殊的数据包，通知对方在线以确保TCP连接的有效性。

### 如何实现心跳机制
有两种方式实现心跳机制：
- 使用TCP协议层面的 keepalive 机制
- 在应用层上自定义的心跳机制

TCP层面的 keepalive 机制我们在之前构建 Netty服务端和客户端启动过程中也有定义，我们需要手动开启，示例如下：
```
// 设置TCP的长连接，默认的 keepalive的心跳时间是两个小时
childOption(ChannelOption.SO_KEEPALIVE, true)
```
除了开启 TCP协议的 keepalive 之外，在我研究了github的一些开源Demo发现，人们往往也会自定义自己的心跳机制，定义心跳数据包。而Netty也提供了 **IdleStateHandler** 来实现心跳机制

### Netty 实现心跳机制
下面来看看客户端如何实现心跳机制：
```java
@Slf4j
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.WRITER_IDLE) {
                log.info("已经10s没有发送消息给服务端");
                //向服务端送心跳包
                //这里使用 protobuf定义的消息格式
                MessageBase.Message heartbeat = new MessageBase.Message().toBuilder().setCmd(MessageBase.Message.CommandType.HEARTBEAT_REQUEST)
                        .setRequestId(UUID.randomUUID().toString())
                        .setContent("heartbeat").build();
                //发送心跳消息，并在发送失败时关闭该连接
                ctx.writeAndFlush(heartbeat).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
```
我们这里创建了一个ChannelHandler类并重写了`userEventTriggered`方法，在该方法里实现发送心跳数据包的逻辑，同时将 `IdleStateEvent`类加入逻辑处理链上。

实际上是当连接空闲时间太长时，将会触发一个 `IdleStateEvent`事件，然后我们调用 `userEventTriggered`来处理该 `IdleStateEvent`事件。

当启动客户端和服务端之后，控制台打印心跳消息如下：
```
2018-10-28 16:30:46.825  INFO 42648 --- [ntLoopGroup-2-1] c.pjmike.server.client.HeartbeatHandler  : 已经10s没有发送消息给服务端
2018-10-28 16:30:47.176  INFO 42648 --- [ntLoopGroup-4-1] c.p.server.server.NettyServerHandler     : 收到客户端发来的心跳消息：requestId: "80723780-2ce0-4b43-ad3a-53060a6e81ab"
cmd: HEARTBEAT_REQUEST
content: "heartbeat"
```

上面我们只讨论了客户端发送心跳消息给服务端，那么服务端还需要发心跳消息给客户端吗？

一般情况是，对于长连接而言，一种方案是两边都发送心跳消息，另一种是服务端作为被动接收一方，如果一段时间内服务端没有收到心跳包那么就直接断开连接。

我们这里采用第二种方案，只需要客户端发送心跳消息，然后服务端被动接收，然后设置一段时间，在这段时间内如果服务端没有收到任何消息，那么就主动断开连接，这也就是后面要说的 **空闲检测**

## Netty 客户端断线重连
一般有以下两种情况，Netty 客户端需要重连服务端：
- Netty 客户端启动时，服务端挂掉，连不上服务端
- 在程序运行过程中，服务端突然挂掉

第一种情况实现 `ChannelFutureListener`用来监测连接是否成功，不成功就进行断连重试机制，代码如下：
```java
@Component
@Slf4j
public class NettyClient  {
    private EventLoopGroup group = new NioEventLoopGroup();
    @Value("${netty.port}")
    private int port;
    @Value("${netty.host}")
    private String host;
    private SocketChannel socketChannel;

    public void sendMsg(MessageBase.Message message) {
        socketChannel.writeAndFlush(message);
    }

    @PostConstruct
    public void start()  {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .remoteAddress(host, port)
                .handler(new ClientHandlerInitilizer());
        ChannelFuture future = bootstrap.connect();
        //客户端断线重连逻辑
        future.addListener((ChannelFutureListener) future1 -> {
            if (future1.isSuccess()) {
                log.info("连接Netty服务端成功");
            } else {
                log.info("连接失败，进行断线重连");
                future1.channel().eventLoop().schedule(() -> start(), 20, TimeUnit.SECONDS);
            }
        });
        socketChannel = (SocketChannel) future.channel();
    }
}
```
ChannelFuture添加一个监听器，如果客户端连接服务端失败，调用 `channel().eventLoop().schedule()`方法执行重试逻辑。


第二种情况是运行过程中 服务端突然挂掉了，这种情况我们在处理数据读写的Handler中实现，代码如下：
```java
@Slf4j
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    @Autowired
    private NettyClient nettyClient;
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.WRITER_IDLE) {
                log.info("已经10s没有发送消息给服务端");
                //向服务端送心跳包
                MessageBase.Message heartbeat = new MessageBase.Message().toBuilder().setCmd(MessageBase.Message.CommandType.HEARTBEAT_REQUEST)
                        .setRequestId(UUID.randomUUID().toString())
                        .setContent("heartbeat").build();
                //发送心跳消息，并在发送失败时关闭该连接
                ctx.writeAndFlush(heartbeat).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //如果运行过程中服务端挂了,执行重连机制
        EventLoop eventLoop = ctx.channel().eventLoop();
        eventLoop.schedule(() -> nettyClient.start(), 10L, TimeUnit.SECONDS);
        super.channelInactive(ctx);
    }
}
```
我们这里直接在实现心跳机制的 Handler中重写`channelInactive`方法，然后在该方法中执行重试逻辑，这里注入了 `NettyClient`类，目的是方便调用 `NettyClient`的`start()`方法重新连接服务端

`channelInactive()`方法是指如果当前Channel没有连接到远程节点，那么该方法将会被调用。

## 服务端空闲检测
空闲检测是什么？实际上空闲检测是每隔一段时间，检测这段时间内是否有数据读写。比如，服务端检测一段时间内，是否收到客户端发送来的数据，如果没有，就及时释放资源，关闭连接。

对于空闲检测，Netty 特地提供了 **IdleStateHandler** 来实现这个功能。下面的代码参考自[《Netty 入门与实战：仿写微信 IM 即时通讯系统》](https://juejin.im/book/5b4bc28bf265da0f60130116/section/5b4db16de51d4519601ab69f#heading-2)中空闲检测部分的实现：
```java
@Slf4j
public class ServerIdleStateHandler extends IdleStateHandler {
    /**
     * 设置空闲检测时间为 30s
     */
    private static final int READER_IDLE_TIME = 30;
    public ServerIdleStateHandler() {
        super(READER_IDLE_TIME, 0, 0, TimeUnit.SECONDS);
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        log.info("{} 秒内没有读取到数据,关闭连接", READER_IDLE_TIME);
        ctx.channel().close();
```
## Controller方法测试
因为这是 SpringBoot 整合 Netty 的一个Demo,我们创建一个`Controller`方法对Netty 服务端与客户端之间的通信进行测试，controller代码如下，非常简单：
```java
@RestController
public class ConsumerController {
    @Autowired
    private NettyClient nettyClient;

    @GetMapping("/send")
    public String send() {
        MessageBase.Message message = new MessageBase.Message()
                .toBuilder().setCmd(MessageBase.Message.CommandType.NORMAL)
                .setContent("hello server")
                .setRequestId(UUID.randomUUID().toString()).build();
        nettyClient.sendMsg(message);
        return "send ok";
    }
}
```
注入 `NettyClient`，调用其 `sendMsg`方法发送消息，结果如下：
```
c.p.server.server.NettyServerHandler     : 收到客户端的业务消息：requestId: "aba74c28-1b6e-42b3-9f27-889e7044dcbf"
content: "hello server"
```

## 小结
上面详细阐述了 如何用 SpringBoot 整合 Netty ,其中借鉴很多前辈大佬的例子与文章，算是初步了解了如何使用 Netty。上文中如有错误之处，欢迎指出。

## 参考资料 & 鸣谢
- [Netty 入门与实战：仿写微信 IM 即时通讯系统](https://juejin.im/book/5b4bc28bf265da0f60130116)
- [Netty Client重连实现](https://colobu.com/2015/08/14/netty-tcp-client-with-reconnect-handling/)
- [Netty(一) SpringBoot 整合长连接心跳机制](https://crossoverjie.top/2018/05/24/netty/Netty(1)TCP-Heartbeat/)
- [浅析 Netty 实现心跳机制与断线重连](https://segmentfault.com/a/1190000006931568)
- [\[转\]Protobuf3 语法指南](https://colobu.com/2017/03/16/Protobuf3-language-guide/)
