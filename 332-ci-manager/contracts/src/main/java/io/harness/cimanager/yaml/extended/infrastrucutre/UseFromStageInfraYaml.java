/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CI)
@Data
@Builder
@JsonTypeName("UseFromStage")
@TypeAlias("useFromStageInfraYaml")
@RecasterAlias("io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml")
public class UseFromStageInfraYaml implements Infrastructure {
  @NotNull private String useFromStage;
  @ApiModelProperty(hidden = true) String uuid;
  @JsonIgnore
  @Override
  public Type getType() {
    return Type.USE_FROM_STAGE;
  }
}
