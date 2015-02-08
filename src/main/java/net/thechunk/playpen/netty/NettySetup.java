package net.thechunk.playpen.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import net.thechunk.playpen.protocol.Protocol;

public class NettySetup {
    // Netty I/O group
    private static final NioEventLoopGroup EVENT_LOOP_GROUP = new NioEventLoopGroup();

    // Shared handlers
    private static final ProtobufVarint32LengthFieldPrepender LENGTH_FIELD_PREPENDER = new ProtobufVarint32LengthFieldPrepender();
    private static final ProtobufEncoder PROTOBUF_ENCODER = new ProtobufEncoder();
    private static final ChannelInitializer<NioSocketChannel> BASE_INITIALIZER = new ChannelInitializer<NioSocketChannel>() {
        @Override
        protected void initChannel(NioSocketChannel channel) throws Exception {
            channel.pipeline().addLast("lengthDecoder", new ProtobufVarint32FrameDecoder());
            channel.pipeline().addLast("protobufDecoder", new ProtobufDecoder(Protocol.AuthenticatedMessage.getDefaultInstance()));
            channel.pipeline().addLast("protobufEncoder", PROTOBUF_ENCODER);
            channel.pipeline().addLast("lengthPrepender", LENGTH_FIELD_PREPENDER);
        }
    };
}
