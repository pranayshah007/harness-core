package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.TAS_BASIC_APP_SETUP)
@TypeAlias("TasBasicAppSetupStepParameters")
@RecasterAlias("io.harness.cdng.tas.TasBasicAppSetupStepParameters")
public class TasBasicAppSetupStepParameters extends TasAppSetupBaseStepInfo implements SpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public TasBasicAppSetupStepParameters(TasInstanceCountType instanceCount,
      ParameterField<Integer> existingVersionToKeep, ParameterField<List<String>> additionalRoutes,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(instanceCount, existingVersionToKeep, additionalRoutes, delegateSelectors);
  }
}
