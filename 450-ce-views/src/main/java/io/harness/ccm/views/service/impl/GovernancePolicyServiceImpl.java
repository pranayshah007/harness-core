/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

import io.harness.ccm.views.dao.PolicyDAO;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.service.GovernancePolicyService;
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
  public boolean delete(String accountId, String uuid) {
    return policyDao.delete(accountId, uuid);
  }

  @Override
  public Policy update(Policy policy) {
    return policyDao.update(policy);
  }

  @Override
  public List<Policy> list(String accountId) {
    return policyDao.list(accountId);
  }

  @Override
  public List<Policy> findByResource(String resource, String accountId) {
    return policyDao.findByResource(resource, accountId);
  }
  @Override
  public List<Policy> findByTag(String tag, String accountId) {
    return policyDao.findByTag(tag, accountId);
  }
  @Override
  public Policy listid(String accountId, String uuid) {
    return policyDao.listid(accountId, uuid);
  }
  @Override
  public List<Policy> findByTagAndResource(String resource, String tag, String accountId) {
    return policyDao.findByTagAndResource(resource, tag, accountId);
  }
  @Override
  public List<Policy> findByStability(String isStablePolicy, String accountId) {
    return policyDao.findByStability(isStablePolicy, accountId);
  }
}
