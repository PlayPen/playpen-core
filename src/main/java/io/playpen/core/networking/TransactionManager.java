package io.playpen.core.networking;

import io.playpen.core.coordinator.PlayPen;
import io.playpen.core.protocol.Commands;
import io.playpen.core.protocol.Protocol;
import lombok.extern.log4j.Log4j2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Log4j2
public class TransactionManager {
    public static final long TRANSACTION_TIMEOUT = 120; // seconds

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

    public TransactionInfo getInfo(String id) {
        return transactions.getOrDefault(id, null);
    }

    public Protocol.Transaction build(String id, Protocol.Transaction.Mode mode, Commands.BaseCommand command) {
        TransactionInfo info = getTransaction(id);
        if(info == null) {
            log.error("Unable to build unknown transaction " + id);
            return null;
        }

        return Protocol.Transaction.newBuilder()
                .setId(info.getId())
                .setMode(mode)
                .setPayload(command)
                .build();
    }

    public TransactionInfo begin() {
        TransactionInfo info = new TransactionInfo();

        info.setId(PlayPen.get().generateId());
        while(transactions.containsKey(info.getId()))
            info.setId(PlayPen.get().generateId());

        transactions.put(info.getId(), info);

        final String tid = info.getId();
        if(PlayPen.get().getScheduler() != null) {
            info.setCancelTask(PlayPen.get().getScheduler().schedule(() -> {
                log.warn("Transaction " + tid + " has been cancelled due to timeout");
                cancel(tid, true);
            }, TRANSACTION_TIMEOUT, TimeUnit.SECONDS));
        }

        return info;
    }

    public boolean send(String id, Protocol.Transaction message, String target) {
        TransactionInfo info = getTransaction(id);
        if(info == null) {
            log.error("Cannot send unknown transaction " + id);
            return false;
        }

        if(!info.getId().equals(message.getId())) {
            log.error("Message id does not match transaction id " + id);
            return false;
        }

        info.setTransaction(message);
        info.setTarget(target);

        if(info.getHandler() != null) {
            info.getHandler().onTransactionSend(this, info);
        }

        if(message.getMode() == Protocol.Transaction.Mode.COMPLETE || message.getMode() == Protocol.Transaction.Mode.SINGLE) {
            complete(info.getId());
        }

        return PlayPen.get().send(message, info.getTarget());
    }

    public boolean cancel(String id) {
        return cancel(id, false);
    }

    public boolean cancel(String id, boolean silentFail) {
        TransactionInfo info = getTransaction(id);
        if(info == null) {
            if(!silentFail) log.error("Cannot cancel unknown transaction " + id);
            return false;
        }

        if(info.getHandler() != null) {
            info.getHandler().onTransactionCancel(this, info);
        }

        if(info.getCancelTask() != null && !info.getCancelTask().isDone()) {
            info.getCancelTask().cancel(false);
        }

        info.setDone(true);

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

        if(info.getCancelTask() != null && !info.getCancelTask().isDone()) {
            info.getCancelTask().cancel(false);
        }

        info.setDone(true);

        transactions.remove(id);
        return true;
    }

    public void receive(Protocol.Transaction message, String from) {
        TransactionInfo info = null;
        switch(message.getMode()) {
            case CREATE:
                if(transactions.containsKey(message.getId())) {
                    log.error("Received CREATE on an id that already exists (" + message.getId() + ")");
                    return;
                }

                info = new TransactionInfo();
                info.setId(message.getId());
                info.setTarget(from);
                transactions.put(info.getId(), info);
                break;

            case SINGLE:
                info = new TransactionInfo();
                info.setDone(true);
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

                info.setDone(true);
                if(!complete(info.getId())) {
                    log.error("Unable to complete transaction " + info.getId());
                    return;
                }
                break;
        }

        PlayPen.get().process(message.getPayload(), info, from);
    }
}
