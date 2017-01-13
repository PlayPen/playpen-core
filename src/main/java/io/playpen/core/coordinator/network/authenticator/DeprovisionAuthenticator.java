package io.playpen.core.coordinator.network.authenticator;

import io.playpen.core.coordinator.network.LocalCoordinator;
import io.playpen.core.networking.TransactionInfo;
import io.playpen.core.protocol.Commands;

public class DeprovisionAuthenticator implements IAuthenticator {

    @Override
    public String getName() {
        return "deprovision";
    }

    @Override
    public boolean hasAccess(Commands.BaseCommand command, TransactionInfo info, LocalCoordinator from) {
        switch (command.getType())
        {
            case SYNC:
            case C_GET_COORDINATOR_LIST:
            case C_DEPROVISION:
            case C_FREEZE_SERVER:
            case C_ACK:
            case C_REQUEST_PACKAGE_LIST:
                return true;
        }

        return false;
    }
}
