package net.thechunk.playpen.networking.netty;

import com.google.protobuf.ExtensionRegistry;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import net.thechunk.playpen.protocol.Commands;
import net.thechunk.playpen.protocol.Coordinator;
import net.thechunk.playpen.protocol.P3;
import net.thechunk.playpen.protocol.Protocol;

public class AuthenticatedMessageInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel channel) throws Exception {
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        Commands.registerAllExtensions(registry);
        Coordinator.registerAllExtensions(registry);
        P3.registerAllExtensions(registry);
        Protocol.registerAllExtensions(registry);

        channel.pipeline().addLast("lengthDecoder", new ProtobufVarint32FrameDecoder());
        channel.pipeline().addLast("protobufDecoder", new ProtobufDecoder(Protocol.AuthenticatedMessage.getDefaultInstance(), registry));

        channel.pipeline().addLast("lengthPrepender", new ProtobufVarint32LengthFieldPrepender());
        channel.pipeline().addLast("protobufEncoder", new ProtobufEncoder());

        channel.pipeline().addLast(new AuthenticatedMessageHandler());
    }
}
