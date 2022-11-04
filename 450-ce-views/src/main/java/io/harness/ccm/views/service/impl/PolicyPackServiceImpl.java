/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.PolicyPackDAO;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyPack;
import io.harness.ccm.views.service.PolicyPackService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

import io.harness.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;

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
  public PolicyPack listName(String accountId, String uuid, boolean create) {
    {
      return policyPackDAO.listName(accountId, uuid, create); }
  }

 @Override
  public PolicyPack listId(String accountId, String name, boolean create) {
    {
      return policyPackDAO.listid(accountId, name, create); }
  }

  @Override
  public List<PolicyPack> list(String accountId,PolicyPack policyPack) {
    { return policyPackDAO.list(accountId,policyPack); }
  }

  @Override
  public void check( List<String> policyPackIdentifier) {
    {   List<PolicyPack> policyPacks= policyPackDAO.check( policyPackIdentifier);
      if (policyPacks.size() != policyPackIdentifier.size()) {
        for (PolicyPack it : policyPacks) {
          log.info("{} {} ", it, it.getUuid());
          policyPackIdentifier.remove(it.getUuid());
        }
        if (policyPackIdentifier.size() != 0) {
          throw new InvalidRequestException("No such policies exist:" + policyPackIdentifier.toString());
        }
      }

    }
  }

  @Override
  public boolean deleteOOTB(String uuid) {
    return policyPackDAO.deleteOOTB(uuid);
  }

  @Override
  public List<PolicyPack> listPacks(String accountId, List<String> policyPackIDs){
  return policyPackDAO.listPacks(accountId, policyPackIDs);
  }
}
