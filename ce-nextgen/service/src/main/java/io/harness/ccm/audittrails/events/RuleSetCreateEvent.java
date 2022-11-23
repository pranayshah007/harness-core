package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.RuleSet;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RuleSetCreateEvent extends RuleSetEvent {
  public static final String RULE_SET_CREATED = "RuleSetCreated";

  public RuleSetCreateEvent(String accountIdentifier, RuleSet ruleSet) {
    super(accountIdentifier, ruleSet);
  }

  @Override
  public String getEventType() {
    return RULE_SET_CREATED;
  }
}
