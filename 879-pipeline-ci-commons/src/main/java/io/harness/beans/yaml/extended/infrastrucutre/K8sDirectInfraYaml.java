/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.SwaggerConstants.BOOLEAN_CLASSPATH;
import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_LIST_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_MAP_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("KubernetesDirect")
@TypeAlias("k8sDirectInfraYaml")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml")
public class K8sDirectInfraYaml implements Infrastructure {
  @Builder.Default @NotNull private Type type = Type.KUBERNETES_DIRECT;
  @NotNull private K8sDirectInfraYamlSpec spec;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @Data
  @SuperBuilder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class K8sDirectInfraYamlSpec {
    @JsonProperty(YamlNode.UUID_FIELD_NAME)
    @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
    @ApiModelProperty(hidden = true)
    String uuid;
    @NotNull @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> connectorRef;
    @NotNull @Size(min = 1) @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> namespace;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<Map<String, String>> annotations;
    @YamlSchemaTypes(value = {string})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<Map<String, String>> labels;
    @YamlSchemaTypes({string})
    @ApiModelProperty(dataType = INTEGER_CLASSPATH)
    private ParameterField<Integer> runAsUser;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> serviceAccountName;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> initTimeout;
    @YamlSchemaTypes(value = {runtime})
    @ApiModelProperty(dataType = STRING_MAP_CLASSPATH)
    private ParameterField<Map<String, String>> nodeSelector;
    @YamlSchemaTypes(value = {runtime})
    @ApiModelProperty(dataType = STRING_LIST_CLASSPATH)
    private ParameterField<List<String>> hostNames;
    @YamlSchemaTypes(value = {runtime})
    @VariableExpression(skipVariableExpression = true)
    @ApiModelProperty(dataType = "[Lio.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;")
    private ParameterField<List<Toleration>> tolerations;
    @YamlSchemaTypes(value = {runtime})
    @ApiModelProperty(dataType = "[Lio.harness.beans.yaml.extended.volumes.CIVolume;")
    ParameterField<List<CIVolume>> volumes;
    @YamlSchemaTypes({runtime})
    @ApiModelProperty(dataType = BOOLEAN_CLASSPATH)
    private ParameterField<Boolean> automountServiceAccountToken;
    @YamlSchemaTypes(value = {runtime})
    @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext")
    ParameterField<SecurityContext> containerSecurityContext;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> priorityClassName;
    @YamlSchemaTypes({runtime})
    @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.infrastrucutre.OSType")
    private ParameterField<OSType> os;
    @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> harnessImageConnectorRef;
  }
}
