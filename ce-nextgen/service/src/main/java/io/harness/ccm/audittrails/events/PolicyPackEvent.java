package io.harness.ccm.audittrails.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import static io.harness.audit.ResourceTypeConstants.GOVERNANCE_POLICY_SET;
import io.harness.ccm.views.entities.PolicyPack;
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
public abstract class PolicyPackEvent implements Event {

    private PolicyPack policyPack;
    private String accountIdentifier;

    public PolicyPackEvent(String accountIdentifier, PolicyPack policyPack) {
        this.accountIdentifier = accountIdentifier;
        this.policyPack = policyPack;
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
        labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, policyPack.getName());
        return Resource.builder().identifier(policyPack.getUuid()).type(GOVERNANCE_POLICY_SET).labels(labels).build();

    }



}
