package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.Rule;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RuleUpdateEvent extends RuleEvent {
  public static final String RULE_UPDATED = "RuleUpdated";

  public RuleUpdateEvent(String accountIdentifier, Rule rules) {
    super(accountIdentifier, rules);
  }

  @Override
  public String getEventType() {
    return RULE_UPDATED;
  }
}
