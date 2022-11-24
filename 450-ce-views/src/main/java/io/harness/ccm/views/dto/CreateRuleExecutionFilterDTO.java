package io.harness.ccm.views.dto;

import io.harness.ccm.views.helper.RuleExecutionFilter;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRuleExecutionFilterDTO {
  @JsonProperty("policyExecutionFilter") @Valid RuleExecutionFilter ruleExecutionFilter;
}
