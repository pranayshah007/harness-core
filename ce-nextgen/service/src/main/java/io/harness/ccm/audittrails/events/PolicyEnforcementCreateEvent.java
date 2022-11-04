package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.PolicyEnforcement;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PolicyEnforcementCreateEvent extends PolicyEnforcementEvent {
    public static final String POLICY_ENFORCEMENT_CREATED = "PolicyEnforcementCreated";

    public PolicyEnforcementCreateEvent(String accountIdentifier, PolicyEnforcement policyEnforcement) {
        super(accountIdentifier, policyEnforcement);
    }

    @Override
    public String getEventType() {
        return POLICY_ENFORCEMENT_CREATED;
    }
}