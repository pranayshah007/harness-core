package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.PolicyEnforcement;

public class PolicyEnforcementDeleteEvent  extends PolicyEnforcementEvent {
    public static final String POLICY_ENFORCEMENT_DELETED = "PolicyEnforcementDeleted";

    public PolicyEnforcementDeleteEvent(String accountIdentifier, PolicyEnforcement policyEnforcement) {
        super(accountIdentifier, policyEnforcement);
    }

    @Override
    public String getEventType() {
        return POLICY_ENFORCEMENT_DELETED;
    }
}