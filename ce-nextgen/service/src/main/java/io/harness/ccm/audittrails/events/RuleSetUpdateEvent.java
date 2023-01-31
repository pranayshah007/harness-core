/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.RuleSet;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RuleSetUpdateEvent extends RuleSetEvent {
  public static final String RULE_SET_UPDATED = "RuleSetUpdated";
  private RuleSet oldRuleSet;

  public RuleSetUpdateEvent(String accountIdentifier, RuleSet ruleSet, RuleSet oldRuleSet) {
    super(accountIdentifier, ruleSet);
    this.oldRuleSet = oldRuleSet;
  }

  @Override
  public String getEventType() {
    return RULE_SET_UPDATED;
  }
}
