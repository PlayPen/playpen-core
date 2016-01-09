package io.playpen.core.networking.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.playpen.core.protocol.Protocol;

public class AuthenticatedMessageInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel channel) throws Exception {
        channel.pipeline().addLast("lengthDecoder", new ProtobufVarint32FrameDecoder());
        channel.pipeline().addLast("protobufDecoder", new ProtobufDecoder(Protocol.AuthenticatedMessage.getDefaultInstance()));

        channel.pipeline().addLast("lengthPrepender", new ProtobufVarint32LengthFieldPrepender());
        channel.pipeline().addLast("protobufEncoder", new ProtobufEncoder());

        channel.pipeline().addLast(new AuthenticatedMessageHandler());
    }
}
