package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.Rule;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RuleDeleteEvent extends RuleEvent {
  public static final String RULE_DELETED = "RuleDeleted";

  public RuleDeleteEvent(String accountIdentifier, Rule rules) {
    super(accountIdentifier, rules);
  }

  @Override
  public String getEventType() {
    return RULE_DELETED;
  }
}
