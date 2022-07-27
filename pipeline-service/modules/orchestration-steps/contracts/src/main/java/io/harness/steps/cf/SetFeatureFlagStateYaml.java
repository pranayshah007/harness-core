/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.cf;

import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("SetFeatureFlagState")
@TypeAlias("SetFeatureFlagStateYaml")
public class SetFeatureFlagStateYaml implements PatchInstruction {
  @Builder.Default
  @NotNull
  @ApiModelProperty(allowableValues = "SetFeatureFlagState")
  private PatchInstruction.Type type = Type.SET_FEATURE_FLAG_STATE;
  @NotNull private String identifier;
  @NotNull private SetFeatureFlagStateYamlSpec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SetFeatureFlagStateYamlSpec {
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> state;
  }
}
