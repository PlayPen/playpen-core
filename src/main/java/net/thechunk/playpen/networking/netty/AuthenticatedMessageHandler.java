package net.thechunk.playpen.networking.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.coordinator.PlayPen;
import net.thechunk.playpen.protocol.Protocol;
import net.thechunk.playpen.utils.AuthUtils;

@Log4j2
public class AuthenticatedMessageHandler extends SimpleChannelInboundHandler<Protocol.AuthenticatedMessage> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Protocol.AuthenticatedMessage msg) throws Exception {
        if(!PlayPen.get().receive(msg, ctx.channel())) {
            log.error("Unable to pass message to PlayPen coordinator");
            return;
        }
    }
}
