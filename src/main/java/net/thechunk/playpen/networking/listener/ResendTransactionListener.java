package net.thechunk.playpen.networking.listener;

import lombok.extern.log4j.Log4j2;
import net.thechunk.playpen.networking.ITransactionListener;
import net.thechunk.playpen.networking.TransactionInfo;
import net.thechunk.playpen.networking.TransactionManager;
import net.thechunk.playpen.protocol.Protocol;

/**
 * Resends transactions on timeout, cancelling if there have been too
 * many failures in a row.
 */
@Log4j2
public class ResendTransactionListener implements ITransactionListener {
    private int failures = 0;
    private int maxFailures = -1;

    public ResendTransactionListener() {}

    public ResendTransactionListener(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    @Override
    public void onTransactionTimeout(TransactionManager tm, TransactionInfo info) {
        log.warn("Timeout on transaction " + info.getId());
        ++failures;
        if(maxFailures > 0 && failures >= maxFailures) {
            log.error("Failing transaction " + info.getId() + " due to failure limit");
            tm.cancel(info.getId());
        }

        tm.send(info.getId(), info.getTransaction(), info.getTarget());
    }

    @Override
    public void onTransactionReceive(TransactionManager tm, TransactionInfo info, Protocol.Transaction message) {
        failures = 0;
    }

    @Override
    public void onTransactionSend(TransactionManager tm, TransactionInfo info) {
        failures = 0;
    }

    @Override
    public void onTransactionComplete(TransactionManager tm, TransactionInfo info) {
    }

    @Override
    public void onTransactionCancel(TransactionManager tm, TransactionInfo info) {
    }
}
