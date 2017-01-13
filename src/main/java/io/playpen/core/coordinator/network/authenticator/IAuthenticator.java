package io.playpen.core.coordinator.network.authenticator;

import io.playpen.core.coordinator.network.LocalCoordinator;
import io.playpen.core.networking.TransactionInfo;
import io.playpen.core.protocol.Commands;

public interface IAuthenticator {
    String getName();
    boolean hasAccess(Commands.BaseCommand command, TransactionInfo info, LocalCoordinator from);
}
