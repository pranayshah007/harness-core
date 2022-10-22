/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import io.harness.ccm.views.dao.PolicyExecutionDAO;
import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.service.PolicyExecutionService;

import com.google.inject.Inject;
import java.util.List;

public class PolicyExecutionServiceImpl implements PolicyExecutionService {
  @Inject private PolicyExecutionDAO policyExecutionDAO;

  @Override
  public boolean save(PolicyExecution policyExecution) {
    return policyExecutionDAO.save(policyExecution);
  }

  @Override
  public PolicyExecution get(String accountId, String uuid) {
    return policyExecutionDAO.get(accountId, uuid);
  }

  @Override
  public List<PolicyExecution> list(String accountId) {
    return policyExecutionDAO.list(accountId);
  }
}
