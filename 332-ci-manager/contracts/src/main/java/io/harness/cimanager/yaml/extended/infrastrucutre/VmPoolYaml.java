/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("Pool")
@TypeAlias("VmPoolYaml")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml")
public class VmPoolYaml implements VmInfraSpec {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  @Builder.Default @NotNull private VmInfraSpec.Type type = VmInfraSpec.Type.POOL;
  @NotNull private VmPoolYamlSpec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class VmPoolYamlSpec {
    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    String uuid;
    private String identifier;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> poolName;
    @YamlSchemaTypes({runtime})
    @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.infrastrucutre.OSType")
    private ParameterField<OSType> os;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> harnessImageConnectorRef;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> initTimeout;
  }
}
