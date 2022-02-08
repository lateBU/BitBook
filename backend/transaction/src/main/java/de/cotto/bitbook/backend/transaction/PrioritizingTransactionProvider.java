package de.cotto.bitbook.backend.transaction;

import de.cotto.bitbook.backend.model.Provider;
import de.cotto.bitbook.backend.model.Transaction;
import de.cotto.bitbook.backend.model.TransactionHash;
import de.cotto.bitbook.backend.request.PrioritizingProvider;
import de.cotto.bitbook.backend.request.ResultFuture;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PrioritizingTransactionProvider extends PrioritizingProvider<TransactionHash, Transaction> {
    public PrioritizingTransactionProvider(List<Provider<TransactionHash, Transaction>> providers) {
        super(providers, "Transaction details");
    }

    public ResultFuture<Transaction> getTransaction(TransactionRequest transactionRequest) {
        return getForRequest(transactionRequest);
    }
}
