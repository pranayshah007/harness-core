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
@JsonTypeName(StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE)
@TypeAlias("ecsBlueGreenCreateServiceStepNode")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepNode")
public class EcsBlueGreenCreateServiceStepNode extends CdAbstractStepNode {
    @JsonProperty("type") @NotNull EcsBlueGreenCreateServiceStepNode.StepType type = EcsBlueGreenCreateServiceStepNode.StepType.EcsBlueGreenCreateService;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    EcsBlueGreenCreateServiceStepInfo ecsBlueGreenCreateServiceStepInfo;
    @Override
    public String getType() {
        return StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE;
    }

    @Override
    public StepSpecType getStepSpecType() {
        return ecsBlueGreenCreateServiceStepInfo;
    }

    enum StepType {
        EcsBlueGreenCreateService (StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE);
        @Getter
        String name;
        StepType(String name) {
            this.name = name;
        }
    }
}
