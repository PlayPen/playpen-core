package net.thechunk.playpen.networking;

import lombok.Data;
import net.thechunk.playpen.protocol.Protocol;

import java.util.concurrent.ScheduledFuture;

@Data
public class TransactionInfo {
    private String id;

    private String target = null;

    private Protocol.Transaction transaction = null;

    private ITransactionListener handler = null;

    private ScheduledFuture cancelTask = null;

    private boolean done = false;
}
