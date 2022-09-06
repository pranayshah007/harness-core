package io.harness.cdng.ecs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.yaml.core.StepSpecType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS)
@TypeAlias("ecsBlueGreenSwapTargetGroupsStepNode")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepNode")
public class EcsBlueGreenSwapTargetGroupsStepNode extends CdAbstractStepNode {
    @JsonProperty("type") @NotNull EcsBlueGreenSwapTargetGroupsStepNode.StepType type = EcsBlueGreenSwapTargetGroupsStepNode.StepType.EcsBlueGreenSwapTargetGroups;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    EcsBlueGreenSwapTargetGroupsStepInfo ecsBlueGreenSwapTargetGroupsStepInfo;

    @Override
    public String getType() {
        return StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS;
    }

    @Override
    public StepSpecType getStepSpecType() {
        return ecsBlueGreenSwapTargetGroupsStepInfo;
    }

    enum StepType {
        EcsBlueGreenSwapTargetGroups(StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS);
        @Getter
        String name;
        StepType(String name) {
            this.name = name;
        }
    }
}
