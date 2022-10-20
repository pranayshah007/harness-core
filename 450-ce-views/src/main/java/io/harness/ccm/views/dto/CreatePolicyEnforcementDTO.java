package io.harness.ccm.views.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.harness.ccm.views.entities.Policy;
import io.harness.ccm.views.entities.PolicyEnforcement;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import javax.validation.Valid;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePolicyEnforcementDTO {

    @JsonProperty("policyEnforcement") @Valid PolicyEnforcement PolicyEnforcement;
}
