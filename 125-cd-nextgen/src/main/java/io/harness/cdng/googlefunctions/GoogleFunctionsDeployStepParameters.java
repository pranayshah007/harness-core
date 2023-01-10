package io.harness.cdng.googlefunctions;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("googleFunctionsDeployStepParameters")
@RecasterAlias("io.harness.cdng.googlefunctions.GoogleFunctionsDeployStepParameters")
public class GoogleFunctionsDeployStepParameters extends GoogleFunctionsDeployBaseStepInfo
  implements GoogleFunctionsSpecParameters {
    @Builder(builderMethodName = "infoBuilder")
    public GoogleFunctionsDeployStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
                                                   ParameterField<String> updateFieldMask) {
        super(delegateSelectors, updateFieldMask);
    }
}
