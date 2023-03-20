/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.dao.RuleExecutionDAO;
import io.harness.ccm.views.dao.RuleSetDAO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.FilterValues;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleExecutionFilter;
import io.harness.ccm.views.helper.RuleExecutionList;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.service.RuleExecutionService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;

public class RuleExecutionServiceImpl implements RuleExecutionService {
  @Inject private RuleExecutionDAO rulesExecutionDAO;
  @Inject private RuleSetDAO ruleSetDAO;
  @Inject private RuleDAO rulesDao;

  @Override
  public String save(RuleExecution rulesExecution) {
    return rulesExecutionDAO.save(rulesExecution);
  }

  @Override
  public RuleExecution get(String accountId, String uuid) {
    return rulesExecutionDAO.get(accountId, uuid);
  }
  @Override
  public List<RuleExecution> list(String accountId) {
    return rulesExecutionDAO.list(accountId);
  }

  @Override
  public RuleExecutionList filterExecution(RuleExecutionFilter rulesExecutionFilter) {
    return rulesExecutionDAO.filterExecution(rulesExecutionFilter);
  }

  @Override
  public FilterValues filterValue(String accountId) {
    FilterValues filterValues = FilterValues.builder().build();
    RuleSetFilter ruleSetFilter = RuleSetFilter.builder().build();
    List<RuleSet> ruleSet = ruleSetDAO.list(accountId, ruleSetFilter).getRuleSet();
    if (ruleSet != null) {
      HashMap<String, String> ruleSetsIds = new HashMap<>();
      for (RuleSet iterate : ruleSet) {
        ruleSetsIds.put(iterate.getUuid(), iterate.getName());
      }
      filterValues.setRuleSetIds(ruleSetsIds);
    }
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    List<Rule> rules = rulesDao.list(governancePolicyFilter).getRules();
    if (rules != null) {
      HashMap<String, String> rulesIds = new HashMap<>();
      for (Rule iterate : rules) {
        rulesIds.put(iterate.getUuid(), iterate.getName());
      }
      filterValues.setRuleIds(rulesIds);
    }

    return filterValues;
  }
}
