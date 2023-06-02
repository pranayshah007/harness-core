/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.volumes;

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
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("HostPath")
@TypeAlias("hostPathYaml")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.yaml.extended.volumes.HostPathYaml")
public class HostPathYaml implements CIVolume {
  @Builder.Default @NotNull private CIVolume.Type type = Type.HOST_PATH;
  @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> mountPath;

  @NotNull private HostPathYamlSpec spec;
  @ApiModelProperty(hidden = true) String uuid;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class HostPathYamlSpec {
    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> path;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> type;
    @ApiModelProperty(hidden = true) String uuid;
  }
}
