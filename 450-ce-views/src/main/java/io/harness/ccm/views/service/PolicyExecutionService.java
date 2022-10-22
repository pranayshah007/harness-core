package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.PolicyExecution;

import java.util.List;

public interface PolicyExecutionService {
  boolean save(PolicyExecution policyExecution);
  List<PolicyExecution> list(String accountId);
}
