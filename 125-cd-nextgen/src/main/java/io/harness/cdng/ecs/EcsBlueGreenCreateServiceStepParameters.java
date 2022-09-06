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
@TypeAlias("ecsBlueGreenCreateServiceStepParameters")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepParameters")
public class EcsBlueGreenCreateServiceStepParameters extends EcsBlueGreenCreateServiceBaseStepInfo implements EcsSpecParameters {
    @Builder(builderMethodName = "infoBuilder")
    public EcsBlueGreenCreateServiceStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
     ParameterField<String> loadBalancer, ParameterField<String> prodListener,
                                                   ParameterField<String> prodListenerRuleArn,
                                                   ParameterField<String> stageListener,
                                                   ParameterField<String> stageListenerRuleArn) {
        super(delegateSelectors, loadBalancer, prodListener, prodListenerRuleArn, stageListener, stageListenerRuleArn);
    }


    public List<String> getCommandUnits() {
        return Arrays.asList(EcsCommandUnitConstants.fetchManifests.toString(), EcsCommandUnitConstants.prepareRollbackData.toString(),
                EcsCommandUnitConstants.deploy.toString());
    }
}
