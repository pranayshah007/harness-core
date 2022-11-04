package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.Policy;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PolicyUpdateEvent extends PolicyEvent {
    public static final String POLICY_UPDATED = "PolicyUpdated";

    public PolicyUpdateEvent(String accountIdentifier, Policy policy) {
        super(accountIdentifier, policy);
    }

    @Override
    public String getEventType() {
        return POLICY_UPDATED;
    }

}
