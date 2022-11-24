package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.RuleSet;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RuleSetUpdateEvent extends RuleSetEvent {
  public static final String RULE_SET_UPDATED = "RuleSetUpdated";

  public RuleSetUpdateEvent(String accountIdentifier, RuleSet ruleSet) {
    super(accountIdentifier, ruleSet);
  }

  @Override
  public String getEventType() {
    return RULE_SET_UPDATED;
  }
}
