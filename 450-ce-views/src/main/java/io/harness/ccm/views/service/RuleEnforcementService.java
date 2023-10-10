/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.helper.EnforcementCount;
import io.harness.ccm.views.helper.EnforcementCountRequest;
import io.harness.ccm.views.helper.ExecutionDetailRequest;
import io.harness.ccm.views.helper.ExecutionDetails;
import io.harness.remote.GovernanceConfig;

import java.util.List;

public interface RuleEnforcementService {
  RuleEnforcement get(String uuid);
  boolean save(RuleEnforcement ruleEnforcement);
  boolean delete(String accountId, String uuid);
  RuleEnforcement update(RuleEnforcement ruleEnforcement);
  RuleEnforcement listName(String accountId, String name, boolean create);
  void checkLimitsAndValidate(RuleEnforcement ruleEnforcement, GovernanceConfig governanceConfig);
  RuleEnforcement listId(String accountId, String uuid, boolean create);
  List<RuleEnforcement> list(String accountId);
  EnforcementCount getCount(String accountId, EnforcementCountRequest enforcementCountRequest);
  ExecutionDetails getDetails(String accountId, ExecutionDetailRequest executionDetailRequest);
  List<RuleEnforcement> listEnforcementsWithGivenRule(String accountId, String ruleId);
  List<RuleEnforcement> listEnforcementsWithGivenTargetAccount(String accountId, String targetAccountId);
  RuleEnforcement removeRuleFromEnforcement(RuleEnforcement ruleEnforcement, String ruleId);
}
