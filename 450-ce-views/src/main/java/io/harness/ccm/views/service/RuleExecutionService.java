/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.entities.RuleRecommendation;
import io.harness.ccm.views.helper.FilterValues;
import io.harness.ccm.views.helper.OverviewExecutionDetails;
import io.harness.ccm.views.helper.RuleExecutionFilter;
import io.harness.ccm.views.helper.RuleExecutionList;

import java.util.List;
import java.util.Map;

public interface RuleExecutionService {
  String save(RuleExecution rulesExecution);
  RuleExecution get(String accountId, String uuid);
  List<RuleExecution> list(String accountId);
  RuleExecutionList filterExecution(RuleExecutionFilter rulesExecutionFilter);
  FilterValues filterValue(String accountId);
  RuleExecutionList getRuleRecommendationDetails(String ruleRecommendationId, String accountId);
  RuleRecommendation getRuleRecommendation(String ruleRecommendationId, String accountId);
  OverviewExecutionDetails getOverviewExecutionDetails(String accountId, RuleExecutionFilter ruleExecutionFilter);
  Map<String, Double> getExecutionCostDetails(String accountId, RuleExecutionFilter ruleExecutionFilter);
}
