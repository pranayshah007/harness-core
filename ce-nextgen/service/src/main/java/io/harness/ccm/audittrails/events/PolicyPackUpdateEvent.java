package io.harness.ccm.audittrails.events;

import io.harness.ccm.views.entities.PolicyPack;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PolicyPackUpdateEvent extends PolicyPackEvent {
    public static final String POLICY_PACK_UPDATED = "PolicyPackUpdated";

    public PolicyPackUpdateEvent(String accountIdentifier, PolicyPack policyPack) {
        super(accountIdentifier, policyPack);
    }

    @Override
    public String getEventType() {
        return POLICY_PACK_UPDATED;
    }

}
