/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.dao;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.ccm.views.entities.PolicyExecution;
import io.harness.ccm.views.service.PolicyExecutionService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PolicyExecutionDAO {
  @Inject private HPersistence hPersistence;
  private PolicyExecutionService policyExecutionService;

  public boolean save(PolicyExecution policyExecution) {
    log.info("created: {}", hPersistence.save(policyExecution));
    return hPersistence.save(policyExecution) != null;
  }

  public List<PolicyExecution> list(String accountId) {
    return hPersistence.createQuery(PolicyExecution.class)
        .field(PolicyExecution.PolicyExecutionKeys.accountId)
        .equal(accountId)
        .asList();
  }

  public PolicyExecution get(String accountId, String uuid) {
    log.info("accountId: {}, uuid: {}", accountId, uuid);
    return hPersistence.createQuery(PolicyExecution.class, excludeValidate)
        .field(PolicyExecution.PolicyExecutionKeys.uuid)
        .equal(uuid)
        .get();
  }
}
