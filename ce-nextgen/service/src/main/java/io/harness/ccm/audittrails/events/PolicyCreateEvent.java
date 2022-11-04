package io.harness.ccm.audittrails.events;

import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.views.entities.Policy;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PolicyCreateEvent extends PolicyEvent {
    public static final String POLICY_CREATED = "PolicyCreated";

    public PolicyCreateEvent(String accountIdentifier, Policy policy) {
        super(accountIdentifier, policy);
    }

    @Override
    public String getEventType() {
        return POLICY_CREATED;
    }
}
