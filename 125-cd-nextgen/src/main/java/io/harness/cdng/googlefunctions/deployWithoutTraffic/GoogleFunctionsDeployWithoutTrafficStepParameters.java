package io.harness.cdng.googlefunctions.deployWithoutTraffic;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.GoogleFunctionsSpecParameters;
import io.harness.cdng.googlefunctions.deploy.GoogleFunctionsDeployBaseStepInfo;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

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
@TypeAlias("googleFunctionsDeployWithoutTrafficStepParameters")
@RecasterAlias("io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStepParameters")
public class GoogleFunctionsDeployWithoutTrafficStepParameters
    extends GoogleFunctionsDeployWithoutTrafficBaseStepInfo implements GoogleFunctionsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public GoogleFunctionsDeployWithoutTrafficStepParameters(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<String> updateFieldMask) {
    super(delegateSelectors, updateFieldMask);
  }
}