/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.PolicyDAO;
import io.harness.ccm.views.entities.GovernancePolicyFilter;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.service.GovernancePolicyService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class GovernancePolicyServiceImpl implements GovernancePolicyService {
  @Inject private PolicyDAO policyDao;

  @Override
  public boolean save(Policy policy) {
    return policyDao.save(policy);
  }

  @Override
  public boolean deleteOOTB( String uuid) {
    return policyDao.deleteOOTB( uuid);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policyDao.delete( accountId, uuid );
  }


  @Override
  public Policy update(Policy policy) {
    return policyDao.update(policy);
  }

  @Override
  public List<Policy> list( GovernancePolicyFilter governancePolicyFilter) {
    return policyDao.list(governancePolicyFilter);
  }

  @Override
  public Policy listid(String accountId, String name, boolean create) {
    return policyDao.listid(accountId, name, create);
  }

  @Override
  public void check(String accountId, List<String> policiesIdentifier) {
    policyDao.check(accountId, policiesIdentifier);
  }
}
