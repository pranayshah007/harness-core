package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.Policy;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PolicyDeleteEvent extends PolicyEvent{
    public static final String POLICY_DELETED = "PolicyDeleted";

    public PolicyDeleteEvent(String accountIdentifier, Policy policy) {
        super(accountIdentifier, policy);
    }

    @Override
    public String getEventType() {
        return POLICY_DELETED;
    }
}
