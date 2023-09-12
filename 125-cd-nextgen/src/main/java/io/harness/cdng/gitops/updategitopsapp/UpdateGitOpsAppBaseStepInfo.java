/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.GITOPS)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("UpdateGitOpsAppBaseStepInfo")
@FieldNameConstants(innerTypeName = "UpdateGitOpsAppBaseStepInfoKeys")
public class UpdateGitOpsAppBaseStepInfo {
  @YamlSchemaTypes(runtime)
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @JsonProperty("applicationName")
  ParameterField<String> applicationName;

  @YamlSchemaTypes(runtime)
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @NotNull
  @JsonProperty("agentId")
  ParameterField<String> agentId;

  @YamlSchemaTypes(runtime)
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @JsonProperty("targetRevision")
  ParameterField<String> targetRevision;

  @YamlSchemaTypes(runtime)
  @ApiModelProperty(dataType = SwaggerConstants.GITOPS_HELM_VALUES_CLASSPATH)
  @JsonProperty("helm")
  ParameterField<HelmValues> helm;

  @YamlSchemaTypes(runtime)
  @ApiModelProperty(dataType = SwaggerConstants.GITOPS_KUSTOMIZE_VALUES_CLASSPATH)
  @JsonProperty("kustomize")
  ParameterField<KustomizeValues> kustomize;
}
