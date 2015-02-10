package net.thechunk.playpen.networking;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.protocol.Protocol;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
public class TransactionManager {
    private static TransactionManager instance = new TransactionManager();

    public static TransactionManager get() {
        return instance;
    }

    private Map<String, TransactionInfo> transactions = new ConcurrentHashMap<>();

    private TransactionManager() {}

    boolean isActive(String id) {
        return transactions.containsKey(id);
    }

    TransactionInfo getTransaction(String id) {
        return transactions.getOrDefault(id, null);
    }

    public TransactionInfo begin(Protocol.Transaction transaction) {
        TransactionInfo info = new TransactionInfo();

        info.setId(Network.get().generateId());
        while(transactions.containsKey(info.getId()))
            info.setId(Network.get().generateId());

        info.setTransaction(transaction);
        transactions.put(info.getId(), info);
        return info;
    }

    public boolean send(String id) {
        TransactionInfo info = getTransaction(id);
        if(info == null) {
            log.error("Cannot send unknown transaction " + id);
            return false;
        }

        if(info.getHandler() != null) {
            info.getHandler().onTransactionSend(this, info);
        }

        throw new NotImplementedException(); // TODO
    }

    public boolean cancel(String id) {
        TransactionInfo info = getTransaction(id);
        if(info == null) {
            log.error("Cannot cancel unknown transaction " + id);
            return false;
        }

        if(info.getHandler() != null) {
            info.getHandler().onTransactionCancel(this, info);
        }

        transactions.remove(id);
        return true;
    }

    public boolean complete(String id) {
        TransactionInfo info = getTransaction(id);
        if(info == null) {
            log.error("Cannot complete unknown transaction " + id);
            return false;
        }

        if(info.getHandler() != null) {
            info.getHandler().onTransactionComplete(this, info);
        }

        transactions.remove(id);
        return true;
    }

    public void receive(Protocol.Transaction message) {
        TransactionInfo info = null;
        switch(message.getMode()) {
            case CREATE:
                if(transactions.containsKey(message.getId())) {
                    log.error("Received CREATE on an id that already exists (" + message.getId() + ")");
                    return;
                }

                info = new TransactionInfo();
                info.setId(message.getId());
                transactions.put(info.getId(), info);
                break;

            case SINGLE:
                info = new TransactionInfo();
                break;

            case CONTINUE:
                info = getTransaction(message.getId());
                if(info == null) {
                    log.error("Received CONTINUE on an id that doesn't exist (" + message.getId() + ")");
                    return;
                }

                if(info.getHandler() != null) {
                    info.getHandler().onTransactionReceive(this, info, message);
                }
                break;

            case COMPLETE:
                info = getTransaction(message.getId());
                if(info == null) {
                    log.error("Received COMPLETE on an id that doesn't exist (" + message.getId() + ")");
                    return;
                }

                if(info.getHandler() != null) {
                    info.getHandler().onTransactionReceive(this, info, message);
                }

                if(!complete(info.getId())) {
                    log.error("Unable to complete transaction " + info.getId());
                    return;
                }
                break;
        }

        // TODO: Dispatch commands
        throw new NotImplementedException();
    }
}
