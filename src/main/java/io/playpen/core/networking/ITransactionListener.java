package io.playpen.core.networking;

import io.playpen.core.protocol.Protocol;

public interface ITransactionListener {
    void onTransactionReceive(TransactionManager tm, TransactionInfo info, Protocol.Transaction message);

    void onTransactionSend(TransactionManager tm, TransactionInfo info);

    void onTransactionComplete(TransactionManager tm, TransactionInfo info);

    void onTransactionCancel(TransactionManager tm, TransactionInfo info);
}
