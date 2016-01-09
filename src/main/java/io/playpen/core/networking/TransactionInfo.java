package io.playpen.core.networking;

import io.playpen.core.protocol.Protocol;
import lombok.Data;

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
