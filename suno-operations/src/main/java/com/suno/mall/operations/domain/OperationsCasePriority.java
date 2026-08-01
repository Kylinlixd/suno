package com.suno.mall.operations.domain;

/**
 * Urgency assigned when operations escalates a cross-domain case.
 */
public enum OperationsCasePriority {
    ROUTINE,
    URGENT;

    public boolean requiresImmediateHandling() {
        return this == URGENT;
    }
}
