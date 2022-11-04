package io.harness.ccm.audittrails.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import static io.harness.audit.ResourceTypeConstants.GOVERNANCE_POLICY;
import static io.harness.audit.ResourceTypeConstants.PERSPECTIVE_BUDGET;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.views.entities.Policy;
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
public abstract class PolicyEvent implements Event {

    private Policy policy;
    private String accountIdentifier;

    public PolicyEvent(String accountIdentifier, Policy policy) {
        this.accountIdentifier = accountIdentifier;
        this.policy = policy;
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
        labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, policy.getName());
        return Resource.builder().identifier(policy.getUuid()).type(GOVERNANCE_POLICY).labels(labels).build();

    }


}
