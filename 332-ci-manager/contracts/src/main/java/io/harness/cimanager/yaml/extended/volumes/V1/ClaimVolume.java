/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.volumes.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@JsonTypeName("claim")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.yaml.extended.volumes.V1.ClaimVolume")
public class ClaimVolume implements Volume {
  @Builder.Default @NotNull Volume.Type type = Type.CLAIM;
  @NotNull ClaimVolumeSpec spec;
  @ApiModelProperty(hidden = true) String uuid;
  @Value
  @Builder
  @AllArgsConstructor
  public static class ClaimVolumeSpec implements Spec {
    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> target;
    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> name;
    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> mode;
  }
}
