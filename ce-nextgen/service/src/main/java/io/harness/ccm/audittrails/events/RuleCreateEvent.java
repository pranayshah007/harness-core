package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.Rule;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RuleCreateEvent extends RuleEvent {
  public static final String RULE_CREATED = "RuleCreated";

  public RuleCreateEvent(String accountIdentifier, Rule rules) {
    super(accountIdentifier, rules);
  }

  @Override
  public String getEventType() {
    return RULE_CREATED;
  }
}
