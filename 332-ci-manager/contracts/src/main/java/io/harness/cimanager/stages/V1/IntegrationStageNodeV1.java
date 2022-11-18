/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cimanager.stages.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.utils.IdentifierGeneratorUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("ci")
@TypeAlias("IntegrationStageNodeV1")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.stages.V1.IntegrationStageNodeV1")
public class IntegrationStageNodeV1 extends AbstractStageNode {
  @JsonProperty("type") @NotNull StepType type = StepType.ci;

  @JsonProperty("spec")
  @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
  IntegrationStageConfigImplV1 stageConfig;

  @Override
  public String getType() {
    return "ci";
  }

  @Override
  public StageInfoConfig getStageInfoConfig() {
    return stageConfig;
  }

  @Override
  public String getIdentifier() {
    return IdentifierGeneratorUtils.getId(this.getName());
  }

  public enum StepType {
    ci("ci");
    @Getter String name;
    StepType(String name) {
      this.name = name;
    }
  }
}
