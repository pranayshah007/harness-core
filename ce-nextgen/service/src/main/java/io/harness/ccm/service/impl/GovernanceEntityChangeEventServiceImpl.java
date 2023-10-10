/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import io.harness.ccm.service.intf.GovernanceEntityChangeEventService;
import io.harness.ccm.views.entities.RuleEnforcement;
import io.harness.ccm.views.service.RuleEnforcementService;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GovernanceEntityChangeEventServiceImpl implements GovernanceEntityChangeEventService {
  @Inject RuleEnforcementService ruleEnforcementService;

  @Override
  public void processConnectorDeleteEvent(String accountId, String targetAccount) {
    List<RuleEnforcement> ruleEnforcements =
        ruleEnforcementService.listEnforcementsWithGivenTargetAccount(accountId, targetAccount);
    ruleEnforcements.forEach(ruleEnforcement -> updateTargetAccountsInRuleEnforcement(ruleEnforcement, targetAccount));
  }

  private void updateTargetAccountsInRuleEnforcement(RuleEnforcement ruleEnforcement, String deletedTargetAccount) {
    List<String> targetAccounts = ruleEnforcement.getTargetAccounts();
    targetAccounts.remove(deletedTargetAccount);
    ruleEnforcement.setTargetAccounts(targetAccounts);
    ruleEnforcementService.update(ruleEnforcement);
  }
}
