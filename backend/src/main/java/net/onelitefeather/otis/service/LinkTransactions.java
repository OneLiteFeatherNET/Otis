package net.onelitefeather.otis.service;

import java.util.function.Supplier;

/**
 * Runs a unit of work in one database transaction: if it throws, everything it wrote is rolled back.
 * <p>
 * A narrow abstraction so the link rules can be unit tested without a database (see
 * {@link #direct()}) while production uses the Hibernate transaction manager explicitly.
 */
public interface LinkTransactions {

    /**
     * @param work the work to run atomically
     * @param <T>  the result type
     * @return the result of {@code work}
     */
    <T> T execute(Supplier<T> work);

    /** @return an implementation without transactions, for tests with in-memory fakes */
    static LinkTransactions direct() {
        return new LinkTransactions() {
            @Override
            public <T> T execute(Supplier<T> work) {
                return work.get();
            }
        };
    }
}
