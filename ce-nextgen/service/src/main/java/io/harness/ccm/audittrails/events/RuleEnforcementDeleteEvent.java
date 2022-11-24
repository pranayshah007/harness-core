package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.RuleEnforcement;

public class RuleEnforcementDeleteEvent extends RuleEnforcementEvent {
  public static final String RULE_ENFORCEMENT_DELETED = "RuleEnforcementDeleted";

  public RuleEnforcementDeleteEvent(String accountIdentifier, RuleEnforcement ruleEnforcement) {
    super(accountIdentifier, ruleEnforcement);
  }

  @Override
  public String getEventType() {
    return RULE_ENFORCEMENT_DELETED;
  }
}