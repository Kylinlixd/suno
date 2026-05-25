package com.suno.mall.config;

/**
 * Marker interface for transactional wrappers.
 * The actual transaction boundaries are now defined via modular annotations:
 * @AuditTransactional, @AuthTransactional, @RecycleTransactional,
 * @ResaleTransactional, @PaymentTransactional, @ValuationTransactional.
 */
public interface TransactionalWrapper {
}
