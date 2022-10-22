package io.harness.ccm.views.service.impl;

import com.google.inject.Inject;
import io.harness.ccm.views.dao.PolicyEnforcementDAO;
import io.harness.ccm.views.dao.PolicyExecutionDAO;
import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.service.PolicyExecutionService;

import java.util.List;

public class PolicyExecutionServiceImpl implements PolicyExecutionService {
    @Inject
    private PolicyExecutionDAO policyExecutionDAO;

    @Override
    public boolean save(PolicyExecution policyExecution) {
        return policyExecutionDAO.save(policyExecution);
    }

    @Override
    public List<PolicyExecution> list(String accountId) {
        return policyExecutionDAO.list(accountId);
    }
}
