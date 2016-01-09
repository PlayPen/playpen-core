package io.playpen.core.networking;

import io.playpen.core.protocol.Protocol;

public abstract class AbstractTransactionListener implements ITransactionListener {
    @Override
    public void onTransactionReceive(TransactionManager tm, TransactionInfo info, Protocol.Transaction message) {
    }

    @Override
    public void onTransactionSend(TransactionManager tm, TransactionInfo info) {
    }

    @Override
    public void onTransactionComplete(TransactionManager tm, TransactionInfo info) {
    }

    @Override
    public void onTransactionCancel(TransactionManager tm, TransactionInfo info) {
    }
}
