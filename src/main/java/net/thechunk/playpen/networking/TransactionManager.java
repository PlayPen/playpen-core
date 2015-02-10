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

    public TransactionInfo begin(Protocol.Transaction transaction) {
        TransactionInfo info = new TransactionInfo();
        info.setId(UUID.randomUUID().toString());
        info.setTransaction(transaction);
        transactions.put(info.getId(), info);
        return info;
    }

    public void send(String id) {
        // TODO
    }

    public void cancel(String id) {
        TransactionInfo info = transactions.getOrDefault(id, null);
        if(info != null) {
            handleTransactionCompletion(info, TransactionCompletion.CANCEL);
        }
    }

    private void handleTransactionCompletion(TransactionInfo info, TransactionCompletion completion) {
        TransactionResult result;
        if(info.getCallback() == null) {
            result = TransactionResult.COMPLETE;
        }
        else {
            result = info.getCallback().apply(completion);
        }

        switch(result) {
            case COMPLETE:
                transactions.remove(info.getId());
                break;

            case RESEND:
                send(info.getId());
                break;
        }
    }
}
