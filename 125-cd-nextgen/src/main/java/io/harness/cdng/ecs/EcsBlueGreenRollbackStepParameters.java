package io.harness.cdng.ecs;


import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import java.util.Arrays;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ecsBlueGreenRollbackStepParameters")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenRollbackStepParameters")
public class EcsBlueGreenRollbackStepParameters extends EcsBlueGreenRollbackBaseStepInfo implements EcsSpecParameters {
    @Builder(builderMethodName = "infoBuilder")
    public EcsBlueGreenRollbackStepParameters(
            ParameterField<List<TaskSelectorYaml>> delegateSelectors, String ecsBlueGreenCreateServiceFnq) {
        super(delegateSelectors, ecsBlueGreenCreateServiceFnq);
    }

    public List<String> getCommandUnits() {
        return Arrays.asList(EcsCommandUnitConstants.rollback.toString());
    }
}
