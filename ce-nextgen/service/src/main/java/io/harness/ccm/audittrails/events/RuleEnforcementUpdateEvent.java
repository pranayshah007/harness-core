package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.RuleEnforcement;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor

public class RuleEnforcementUpdateEvent extends RuleEnforcementEvent {
  public static final String RULE_ENFORCEMENT_UPDATED = "RuleEnforcementUpdated";

  public RuleEnforcementUpdateEvent(String accountIdentifier, RuleEnforcement ruleEnforcement) {
    super(accountIdentifier, ruleEnforcement);
  }

  @Override
  public String getEventType() {
    return RULE_ENFORCEMENT_UPDATED;
  }
}