/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dao.RuleEnforcementDAO;
import io.harness.ccm.views.dao.RuleSetDAO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.EnforcementCount;
import io.harness.ccm.views.helper.EnforcementCountRequest;
import io.harness.ccm.views.helper.ExecutionDetailRequest;
import io.harness.ccm.views.helper.ExecutionDetails;
import io.harness.ccm.views.helper.ExecutionEnforcementDetails;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.ccm.views.service.RuleEnforcementService;
import io.harness.ccm.views.service.RuleSetService;
import io.harness.exception.InvalidRequestException;
import io.harness.remote.GovernanceConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j

public class RuleEnforcementServiceImpl implements RuleEnforcementService {
  @Inject private RuleEnforcementDAO ruleEnforcementDAO;
  @Inject private RuleDAO rulesDAO;
  @Inject private RuleSetDAO ruleSetDAO;
  @Inject private GovernanceRuleService ruleService;
  @Inject private RuleSetService ruleSetService;

  @Override
  public RuleEnforcement get(String uuid) {
    return ruleEnforcementDAO.get(uuid);
  }

  @Override

  public boolean save(RuleEnforcement ruleEnforcement) {
    return ruleEnforcementDAO.save(ruleEnforcement);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return ruleEnforcementDAO.delete(accountId, uuid);
  }

  @Override
  public RuleEnforcement update(RuleEnforcement ruleEnforcement) {
    return ruleEnforcementDAO.update(ruleEnforcement);
  }

  @Override
  public RuleEnforcement listName(String accountId, String name, boolean create) {
    return ruleEnforcementDAO.fetchByName(accountId, name, create);
  }

  @Override
  public void checkLimitsAndValidate(RuleEnforcement ruleEnforcement, GovernanceConfig governanceConfig) {
    if (ruleEnforcement.getRuleIds() != null
        && ruleEnforcement.getRuleIds().size() > governanceConfig.getPoliciesInEnforcement()) {
      throw new InvalidRequestException("Limit of number of rules in an enforcement is exceeded ");
    }
    if (ruleEnforcement.getRuleSetIDs() != null
        && ruleEnforcement.getRuleSetIDs().size() > governanceConfig.getPacksInEnforcement()) {
      throw new InvalidRequestException("Limit of number of Rule Sets in an enforcement is exceeded ");
    }
    if (ruleEnforcement.getTargetAccounts().size() > governanceConfig.getAccountLimit()) {
      throw new InvalidRequestException("Limit of number of target accounts allowed per enforcement is exceeded ");
    }

    if (ruleEnforcement.getTargetRegions().size() > governanceConfig.getRegionLimit()) {
      throw new InvalidRequestException("Limit of target regions allowed per enforcement is exceeded ");
    }
    if (ruleEnforcement.getRuleIds() != null) {
      ruleService.check(ruleEnforcement.getAccountId(), ruleEnforcement.getRuleIds());
    }
    if (ruleEnforcement.getRuleSetIDs() != null) {
      ruleSetService.check(ruleEnforcement.getAccountId(), ruleEnforcement.getRuleSetIDs());
    }
  }

  @Override
  public RuleEnforcement listId(String accountId, String uuid, boolean create) {
    return ruleEnforcementDAO.fetchById(accountId, uuid, create);
  }

  @Override
  public List<RuleEnforcement> list(String accountId) {
    return ruleEnforcementDAO.list(accountId);
  }

  @Override
  public EnforcementCount getCount(String accountId, EnforcementCountRequest enforcementCountRequest) {
    EnforcementCount enforcementCount = EnforcementCount.builder().build();
    log.info("getCount {}", enforcementCountRequest.getRuleIds());
    if (enforcementCountRequest.getRuleIds() != null) {
      enforcementCount.setRuleIds(getRuleCount(accountId, enforcementCountRequest.getRuleIds()));
    }
    if (enforcementCountRequest.getRuleSetIds() != null) {
      enforcementCount.setRuleSetIds(getRuleSetCount(accountId, enforcementCountRequest.getRuleSetIds()));
    }
    return enforcementCount;
  }

  public Map<String, List<String>> getRuleCount(String accountId, List<String> ruleIds) {
    Map<String, List<String>> rulesIds = new HashMap<>();
    List<RuleEnforcement> ruleEnforcements = ruleEnforcementDAO.ruleEnforcement(accountId, ruleIds);
    for (RuleEnforcement it : ruleEnforcements) {
      for (String itr : it.getRuleIds()) {
        rulesIds.computeIfAbsent(itr, k -> new ArrayList<>());
        rulesIds.get(itr).add(it.getUuid());
      }
    }
    log.info("{}", rulesIds);
    return rulesIds;
  }

  public Map<String, List<String>> getRuleSetCount(String accountId, List<String> ruleSetIds) {
    Map<String, List<String>> ruleSetId = new HashMap<>();
    List<RuleEnforcement> ruleEnforcements = ruleEnforcementDAO.ruleSetEnforcement(accountId, ruleSetIds);
    for (RuleEnforcement it : ruleEnforcements) {
      for (String itr : it.getRuleSetIDs()) {
        ruleSetId.computeIfAbsent(itr, k -> new ArrayList<>());
        ruleSetId.get(itr).add(it.getUuid());
      }
    }
    return ruleSetId;
  }
  @Override
  public ExecutionDetails getDetails(String accountId, ExecutionDetailRequest executionDetailRequest) {
    List<RuleEnforcement> ruleEnforcements =
        ruleEnforcementDAO.listAll(accountId, executionDetailRequest.getEnforcementIds());
    ExecutionDetails executionDetails = ExecutionDetails.builder().build();
    List<HashMap<String, ExecutionEnforcementDetails>> executionDetailList = new ArrayList<>();
    log.info("RuleEnforcement: {}", ruleEnforcements);
    for (RuleEnforcement itr : ruleEnforcements) {
      HashMap<String, ExecutionEnforcementDetails> enforcementIds = new HashMap<>();
      ExecutionEnforcementDetails executionEnforcementDetails = ExecutionEnforcementDetails.builder().build();
      if (itr.getRuleIds() != null) {
        List<Rule> rules = rulesDAO.check(accountId, itr.getRuleIds());
        HashMap<String, String> rulesIds = new HashMap<>();
        for (Rule iterate : rules) {
          rulesIds.put(iterate.getUuid(), iterate.getName());
        }
        log.info("rules: {}", rulesIds);
        executionEnforcementDetails.setRuleIds(rulesIds);
      }
      if (itr.getRuleSetIDs() != null) {
        List<RuleSet> ruleSets = ruleSetDAO.check(accountId, itr.getRuleSetIDs());
        HashMap<String, String> ruleSetsIds = new HashMap<>();
        for (RuleSet iterate : ruleSets) {
          ruleSetsIds.put(iterate.getUuid(), iterate.getName());
        }
        log.info("rules packs: {}", ruleSetsIds);
        executionEnforcementDetails.setRuleSetIds(ruleSetsIds);
      }
      executionEnforcementDetails.setSchedule(itr.getExecutionSchedule());
      executionEnforcementDetails.setAccounts(itr.getTargetAccounts());
      executionEnforcementDetails.setDescription(itr.getDescription());
      executionEnforcementDetails.setRegions(itr.getTargetRegions());
      executionEnforcementDetails.setEnforcementName(itr.getName());
      executionEnforcementDetails.setIsEnabled(itr.getIsEnabled());
      executionEnforcementDetails.setIsDryRun(itr.getIsDryRun());
      log.info("executionEnforcementDetails: {}", executionEnforcementDetails);
      enforcementIds.put(itr.getUuid(), executionEnforcementDetails);
      executionDetailList.add(enforcementIds);
    }
    executionDetails.setEnforcementIds(executionDetailList);
    return executionDetails;
  }
}
