package com.suno.mall.operations.application;

import com.suno.mall.operations.domain.OperationsCasePriority;

/**
 * Application boundary for escalating a cross-domain operations case.
 */
public interface OperationsCaseEscalationUseCase {

    void escalate(String caseId, OperationsCasePriority priority);
}
