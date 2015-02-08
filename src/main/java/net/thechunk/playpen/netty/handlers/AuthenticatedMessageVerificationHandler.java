package net.thechunk.playpen.netty.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;

public class AuthenticatedMessageVerificationHandler extends SimpleChannelInboundHandler<Protocol.AuthenticatedMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Protocol.AuthenticatedMessage msg) throws Exception {
        if (!AuthUtils.validateHash(msg)) {
            throw new DecoderException("Hash " + msg.getHash() + " is invalid (correct hash: " +
                    AuthUtils.createHash(msg.getUuid(), msg.getPayload().toByteArray()));
        }
    }
}
