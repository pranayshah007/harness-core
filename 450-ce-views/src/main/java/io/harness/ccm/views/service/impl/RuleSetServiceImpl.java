/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.RuleSetDAO;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.helper.RuleSetList;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class RuleSetServiceImpl implements RuleSetService {
  @Inject private RuleSetDAO ruleSetDAO;

  @Override

  public boolean save(RuleSet ruleSet) {
    return ruleSetDAO.save(ruleSet);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return ruleSetDAO.delete(accountId, uuid);
  }

  @Override
  public RuleSet update(String accountId, RuleSet ruleSet) {
    return ruleSetDAO.update(accountId, ruleSet);
  }

  @Override
  public RuleSet fetchByName(String accountId, String uuid, boolean create) {
    return ruleSetDAO.fetchByName(accountId, uuid, create);
  }

  @Override
  public RuleSet fetchById(String accountId, String name, boolean create) {
    return ruleSetDAO.fetchById(accountId, name, create);
  }

  @Override
  public RuleSetList list(String accountId, RuleSetFilter ruleSet) {
    return ruleSetDAO.list(accountId, ruleSet);
  }

  @Override
  public void check(String accountId, List<String> ruleSetIdentifier) {
    List<RuleSet> ruleSets = ruleSetDAO.check(accountId, ruleSetIdentifier);
    if (ruleSets.size() != ruleSetIdentifier.size()) {
      for (RuleSet it : ruleSets) {
        log.info("{} {} ", it, it.getUuid());
        ruleSetIdentifier.remove(it.getUuid());
      }
      if (!ruleSetIdentifier.isEmpty()) {
        throw new InvalidRequestException("No such ruleSet exist:" + ruleSetIdentifier.toString());
      }
    }
  }

  @Override
  public boolean deleteOOTB(String accountId, String uuid) {
    return ruleSetDAO.deleteOOTB(accountId, uuid);
  }

  @Override
  public List<RuleSet> listPacks(String accountId, List<String> ruleSetIDs) {
    return ruleSetDAO.listPacks(accountId, ruleSetIDs);
  }
}
