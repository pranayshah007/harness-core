/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.entities.PolicyExecutionFilter;

import java.util.List;

public interface PolicyExecutionService {
  boolean save(PolicyExecution policyExecution);
  PolicyExecution get(String accountId, String uuid);
  List<PolicyExecution> list(String accountId);
//  List<PolicyExecution> filterAccountName(List<String> Accounts);
  List<PolicyExecution> filterExecution(PolicyExecutionFilter policyExecutionFilter);
}
