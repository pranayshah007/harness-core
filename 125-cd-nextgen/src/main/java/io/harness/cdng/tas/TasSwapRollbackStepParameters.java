package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.tas.TasSwapRollbackStepParameters")
public class TasSwapRollbackStepParameters extends TasSwapRollbackBaseStepInfo implements SpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public TasSwapRollbackStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors, String tasRollbackFqn,
      String tasSwapRoutesFqn, String tasBGSetupFqn, String tasBasicSetupFqn, String tasCanarySetupFqn,
      String tasResizeFqn, ParameterField<Boolean> upsizeInActiveApp) {
    super(delegateSelectors, tasRollbackFqn, tasSwapRoutesFqn, tasBGSetupFqn, tasBasicSetupFqn, tasCanarySetupFqn,
        tasResizeFqn, upsizeInActiveApp);
  }
}
