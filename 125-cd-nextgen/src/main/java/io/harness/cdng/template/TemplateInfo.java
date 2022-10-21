package io.harness.cdng.template;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.creation.PlanCreatorUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(PlanCreatorUtils.TEMPLATE_TYPE)
@TypeAlias("TemplatedStepNode")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.template.TemplateInfo")
public class TemplateInfo {
  @JsonProperty("templateRef") String templateRef;
  @JsonProperty("versionLabel") String versionLabel;
}
