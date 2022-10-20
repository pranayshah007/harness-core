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
import io.harness.ccm.views.dao.PolicyPackDAO;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.service.PolicyPackService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Singleton
@OwnedBy(CE)
public class PolicyPackServiceImpl implements PolicyPackService {
  @Inject private PolicyPackDAO policyPackDAO;

  @Override

  public boolean save(PolicyPack policyPack) {
    return policyPackDAO.save(policyPack);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policyPackDAO.delete(accountId, uuid);
  }

  @Override
  public PolicyPack update(PolicyPack policyPack) {
    { return policyPackDAO.update(policyPack); }
  }

  @Override
  public PolicyPack listid(String accountId, String uuid) {
    {
      return policyPackDAO.listid(accountId,uuid); }
  }

  @Override
  public void check(String accountId, List<String> policiesIdentifier) {
    {
      policyPackDAO.check(accountId,policiesIdentifier); }
  }

  @Override
  public List<PolicyPack> list(String accountId) {
    {
      return policyPackDAO.list(accountId); }
  }
}
