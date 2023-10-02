/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import io.harness.ccm.views.entities.RuleSet;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleSetFilter;
import io.harness.ccm.views.helper.RuleSetList;

import java.util.List;
import java.util.Set;

public interface RuleSetService {
  boolean save(RuleSet ruleSet);
  boolean delete(String accountId, String uuid);
  RuleSet update(String accountId, RuleSet ruleSet);
  RuleSet fetchByName(String accountId, String name, boolean create);
  RuleSet fetchById(String accountId, String uuid, boolean create);
  RuleSetList list(String accountId, RuleSetFilter ruleSet);
  void check(String accountId, List<String> ruleSetIdentifier);
  boolean deleteOOTB(String accountId, String uuid);
  List<RuleSet> listPacks(String accountId, List<String> ruleSetIDs);
  void validateCloudProvider(
      String accountId, Set<String> rulesIdentifier, RuleCloudProviderType ruleCloudProviderType);
}
