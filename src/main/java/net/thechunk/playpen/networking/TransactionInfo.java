package net.thechunk.playpen.networking;

import lombok.Data;
import net.thechunk.playpen.protocol.Protocol;

@Data
public class TransactionInfo {
    private String id;
    private float timeout = -1;
    private TransactionMode mode;
    private Protocol.Transaction transaction;
}
