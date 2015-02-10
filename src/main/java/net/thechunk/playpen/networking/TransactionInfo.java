package net.thechunk.playpen.networking;

import lombok.Data;
import net.thechunk.playpen.protocol.Protocol;

import java.util.concurrent.Callable;
import java.util.function.Function;

@Data
public class TransactionInfo {
    private String id;
    private float timeout = -1;
    private Protocol.Transaction transaction;
    private Function<TransactionCompletion, TransactionResult> callback;
}
