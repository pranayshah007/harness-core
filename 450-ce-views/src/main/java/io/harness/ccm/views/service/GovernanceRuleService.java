/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleList;

import java.util.List;

public interface GovernanceRuleService {
  boolean save(Rule policy);
  boolean delete(String accountId, String uuid);
  Rule update(Rule policy, String accountId);
  RuleList list(GovernanceRuleFilter governancePolicyFilter);
  List<Rule> list(String accountId, List<String> uuid);
  Rule fetchByName(String accountId, String name, boolean create);
  Rule fetchById(String accountId, String name, boolean create);
  void check(String accountId, List<String> policiesIdentifier);
  void customRuleLimit(String accountId);
}
