/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.nodes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.SSCSGenerationStepInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.timeout.Timeout;
import lombok.*;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static io.harness.annotations.dev.HarnessTeam.CI;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("SSCSGeneration")
@TypeAlias("SSCSGenerationStepNode")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.nodes.SSCSGenerationStepNode")
public class SSCSGenerationStepNode extends CIAbstractStepNode {
  @JsonProperty("type") @NotNull SSCSGenerationStepNode.StepType type = SSCSGenerationStepNode.StepType.SSCSGeneration;
  @NotNull
  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  SSCSGenerationStepInfo sscsGenerationStepInfo;
  @Override
  public String getType() {
    return CIStepInfoType.SSCSGeneration.getDisplayName();
  }

  @Override
  public StepSpecType getStepSpecType() {
    return sscsGenerationStepInfo;
  }

  public enum StepType {
    SSCSGeneration(CIStepInfoType.SSCSGeneration.getDisplayName());
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }

  /*@Builder
  public SSCSGenerationStepNode(String uuid, String identifier, String name, List<FailureStrategyConfig> failureStrategies,
                                SSCSGenerationStepInfo sscsGenerationStepInfo, SSCSGenerationStepNode.StepType type, ParameterField<Timeout> timeout) {
    this.setFailureStrategies(failureStrategies);
    this.sscsGenerationStepInfo = sscsGenerationStepInfo;
    this.type = type;
    this.setFailureStrategies(failureStrategies);
    this.setTimeout(timeout);
    this.setUuid(uuid);
    this.setIdentifier(identifier);
    this.setName(name);
    this.setDescription(getDescription());
  }*/
}
