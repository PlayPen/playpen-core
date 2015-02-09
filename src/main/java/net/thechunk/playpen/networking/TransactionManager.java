package net.thechunk.playpen.networking;

import net.thechunk.playpen.protocol.Protocol;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionManager {
    private static TransactionManager instance = new TransactionManager();

    public static TransactionManager get() {
        return instance;
    }

    private Map<String, TransactionInfo> transactions = new ConcurrentHashMap<>();

    private TransactionManager() {}

    public TransactionInfo send(Protocol.Transaction transaction) {
        TransactionInfo info = new TransactionInfo();
        info.setId(UUID.randomUUID().toString());
        info.setMode(TransactionMode.SEND);
        info.setTransaction(transaction);
        transactions.put(info.getId(), info);
        return info;
    }

    public void finish(String id) {
        transactions.remove(id);
    }

    public TransactionInfo receive(String id, Protocol.Transaction transaction) {
        TransactionInfo info = new TransactionInfo();
        info.setId(id);
        info.setTimeout(-1);
        info.setMode(TransactionMode.RECEIVE);
        info.setTransaction(transaction);
        transactions.put(info.getId(), info); // we WANT to replace previous entries of the same transaction
        return info;
    }
}
