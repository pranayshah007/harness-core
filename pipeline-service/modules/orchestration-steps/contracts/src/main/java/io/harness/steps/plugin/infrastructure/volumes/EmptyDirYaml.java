/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin.infrastructure.volumes;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.validator.ResourceValidatorConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("EmptyDir")
@TypeAlias("emptyDirYaml")
@OwnedBy(CI)
@RecasterAlias("io.harness.steps.plugin.infrastructure.volumes.EmptyDirYaml")
@Deprecated
public class EmptyDirYaml implements ContainerVolume {
  @Builder.Default @NotNull private ContainerVolume.Type type = Type.EMPTY_DIR;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> mountPath;
  @NotNull private EmptyDirYamlSpec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EmptyDirYamlSpec {
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> medium;
    @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
    @Pattern(regexp = ResourceValidatorConstants.STORAGE_PATTERN)
    private ParameterField<String> size;
  }
}
