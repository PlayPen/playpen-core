package net.thechunk.playpen.networking;

import net.thechunk.playpen.protocol.Protocol;

public interface ITransactionListener {
    void onTransactionTimeout(TransactionManager tm, TransactionInfo info);

    void onTransactionReceive(TransactionManager tm, TransactionInfo info, Protocol.Transaction message);

    void onTransactionSend(TransactionManager tm, TransactionInfo info);

    void onTransactionComplete(TransactionManager tm, TransactionInfo info);

    void onTransactionCancel(TransactionManager tm, TransactionInfo info);
}
