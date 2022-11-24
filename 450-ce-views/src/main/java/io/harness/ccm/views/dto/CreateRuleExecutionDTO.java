package io.harness.ccm.views.dto;

import io.harness.ccm.views.entities.RuleExecution;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRuleExecutionDTO {
  @JsonProperty("RuleExecution") @Valid RuleExecution ruleExecution;
}
