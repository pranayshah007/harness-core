package io.harness.ccm.views.helper;

import io.harness.ccm.views.entities.RuleExecution;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Enforcement Count")

public final class RuleExecutionList {
  @Schema(description = "Total items") int totalItems;
  @Schema(description = "List of rules executions") List<RuleExecution> ruleExecution;

  public RuleExecutionList toDTO() {
    return RuleExecutionList.builder().totalItems(getTotalItems()).ruleExecution(getRuleExecution()).build();
  }
}