package io.harness.cdng.ecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
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
@TypeAlias("ecsRollingRollbackStepParameters")
@RecasterAlias("io.harness.cdng.ecs.EcsRollingRollbackStepParameters")
public class EcsRollingRollbackStepParameters extends EcsRollingRollbackBaseStepInfo implements EcsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public EcsRollingRollbackStepParameters(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String ecsRollingRollbackFnq) {
    super(delegateSelectors, ecsRollingRollbackFnq);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(EcsCommandUnitConstants.rollback.toString());
  }
}
