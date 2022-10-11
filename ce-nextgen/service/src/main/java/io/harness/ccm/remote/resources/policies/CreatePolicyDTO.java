package io.harness.ccm.remote.resources.policies;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

import javax.validation.Valid;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreatePolicyDTO{
    @JsonProperty("policies") @Valid PolicyStore policyStore;
}
