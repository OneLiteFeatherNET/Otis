package net.onelitefeather.otis.service;

import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.hibernate.Session;

import java.util.function.Supplier;

/**
 * Runs link work in a transaction of the Hibernate datasource {@code default}.
 * <p>
 * The classpath carries both {@code micronaut-data-spring-jpa} and {@code micronaut-data-tx-hibernate}, which
 * register two {@code @Primary} transaction operations beans; declarative {@code @Transactional} is therefore
 * ambiguous. Injecting {@code TransactionOperations<Session>} by type with the datasource qualifier
 * {@code default} resolves to {@code SpringHibernateTransactionOperations} (verified when this class was
 * written); the Micronaut Data repositories join its transaction, which
 * {@code LinkTransactionsRollbackTest} proves by rolling back a code claim.
 */
@Singleton
class HibernateLinkTransactions implements LinkTransactions {

    private final TransactionOperations<Session> operations;

    @Inject
    HibernateLinkTransactions(@Named("default") TransactionOperations<Session> operations) {
        this.operations = operations;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return operations.executeWrite(_ -> work.get());
    }
}
