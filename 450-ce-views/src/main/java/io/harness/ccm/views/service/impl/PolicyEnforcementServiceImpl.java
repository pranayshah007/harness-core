/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import io.harness.ccm.views.dao.PolicyEnforcementDAO;
import io.harness.ccm.views.entities.EnforcementCount;
import io.harness.ccm.views.entities.EnforcementCountRequest;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.ccm.views.service.PolicyEnforcementService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j

public class PolicyEnforcementServiceImpl implements PolicyEnforcementService {
  @Inject private PolicyEnforcementDAO policyEnforcementDAO;

  @Override
  public PolicyEnforcement get(String uuid) {
    return policyEnforcementDAO.get(uuid);
  }

  @Override

  public boolean save(PolicyEnforcement policyEnforcement) {
    return policyEnforcementDAO.save(policyEnforcement);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return policyEnforcementDAO.delete(accountId, uuid);
  }

  @Override
  public PolicyEnforcement update(PolicyEnforcement policyEnforcement) {
    { return policyEnforcementDAO.update(policyEnforcement); }
  }

  @Override
  public PolicyEnforcement listName(String accountId, String name, boolean create) {
    { return policyEnforcementDAO.listName(accountId, name, create); }
  }

  @Override
  public PolicyEnforcement listId(String accountId, String uuid, boolean create) {
    { return policyEnforcementDAO.listid(accountId, uuid, create); }
  }

  @Override
  public List<PolicyEnforcement> list(String accountId) {
    { return policyEnforcementDAO.list(accountId); }
  }

  @Override
  public EnforcementCount getCount(String accountId, EnforcementCountRequest enforcementCountRequest) {
    EnforcementCount enforcementCount = EnforcementCount.builder().build();
    log.info("getCount {}", enforcementCountRequest.getPolicyIds());
    if (enforcementCountRequest.getPolicyIds() != null) {
      log.info("getPolicyId");
      HashMap<String, List<String>> policyIds = new HashMap<>();
      List<PolicyEnforcement> policyEnforcements =
          policyEnforcementDAO.policyEnforcementCount(accountId, enforcementCountRequest.getPolicyIds());
      for (PolicyEnforcement it : policyEnforcements) {
        for (String itr : it.getPolicyIds()) {
          if (!policyIds.containsKey(itr)) {
            policyIds.put(itr, new ArrayList<String>());
          }
          policyIds.get(itr).add(it.getUuid());
        }
      }
      log.info("{}", policyIds);
      enforcementCount.setPolicyIds(policyIds);
    }
    if (enforcementCountRequest.getPolicyPackIds() != null) {
      log.info("getPolicyPackId {}", enforcementCountRequest.getPolicyPackIds());
      HashMap<String, List<String>> policyPackId = new HashMap<>();
      List<PolicyEnforcement> policyEnforcements =
          policyEnforcementDAO.policyPackEnforcementCount(accountId, enforcementCountRequest.getPolicyPackIds());
      for (PolicyEnforcement it : policyEnforcements) {
        for (String itr : it.getPolicyPackIDs()) {
          if (!policyPackId.containsKey(itr)) {
            policyPackId.put(itr, new ArrayList<String>());
          }
          policyPackId.get(itr).add(it.getUuid());
        }
      }
      log.info("{}", policyPackId);
      enforcementCount.setPolicyPackIds(policyPackId);
    }

    return enforcementCount;
  }
}
