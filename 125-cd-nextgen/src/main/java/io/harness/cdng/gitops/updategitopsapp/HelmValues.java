/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.updategitopsapp;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Data
@Builder
@OwnedBy(HarnessTeam.GITOPS)
@RecasterAlias("io.harness.cdng.gitops.updategitopsapp.HelmValues")
public class HelmValues {
  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.GITOPS_HELM_PARAMS_LIST_CLASSPATH)
  @JsonProperty("parameters")
  ParameterField<List<HelmParameters>> parameters;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.GITOPS_HELM_FILE_PARAMS_LIST_CLASSPATH)
  @JsonProperty("fileParameters")
  ParameterField<List<HelmFileParameters>> fileParameters;

  @YamlSchemaTypes({runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  @JsonProperty("valueFiles")
  ParameterField<List<String>> valueFiles;
}
