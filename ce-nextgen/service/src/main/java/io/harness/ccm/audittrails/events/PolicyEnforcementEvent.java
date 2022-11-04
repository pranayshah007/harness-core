package io.harness.ccm.audittrails.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import static io.harness.audit.ResourceTypeConstants.GOVERNANCE_POLICY;
import static io.harness.audit.ResourceTypeConstants.GOVERNANCE_POLICY_ENFORCEMENT;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyEnforcement;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor

public abstract class PolicyEnforcementEvent implements Event {

    private PolicyEnforcement policyEnforcement;
    private String accountIdentifier;

    public PolicyEnforcementEvent(String accountIdentifier, PolicyEnforcement policyEnforcement) {
        this.accountIdentifier = accountIdentifier;
        this.policyEnforcement = policyEnforcement;
    }


    @Override
    @JsonIgnore
    public ResourceScope getResourceScope() {
        return new AccountScope(accountIdentifier);
    }

    @Override
    @JsonIgnore
    public Resource getResource() {
        Map<String, String> labels = new HashMap<>();
        labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, policyEnforcement.getName());
        return Resource.builder().identifier(policyEnforcement.getUuid()).type(GOVERNANCE_POLICY_ENFORCEMENT).labels(labels).build();

    }


}
