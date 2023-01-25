/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.codehaus.jackson.annotate.JsonProperty;

@Data
@OwnedBy(STO)
public class STOYamlFODToolData {
  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "app_name")
  @JsonProperty("app_name")
  protected ParameterField<String> appName;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "owner_id")
  @JsonProperty("owner_id")
  protected ParameterField<String> ownerId;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "lookup_type")
  @JsonProperty("lookup_type")
  protected ParameterField<String> lookupType;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "audit_type")
  @JsonProperty("audit_type")
  protected ParameterField<String> auditType;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "scan_type")
  @JsonProperty("scan_type")
  protected ParameterField<String> scanType;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "scan_settings")
  @JsonProperty("scan_settings")
  protected ParameterField<String> scanSettings;

  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("entitlement")
  protected ParameterField<String> entitlement;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "data_center")
  @JsonProperty("data_center")
  protected ParameterField<String> dataCenter;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "release_name")
  @JsonProperty("release_name")
  protected ParameterField<String> releaseName;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "target_language")
  @JsonProperty("target_language")
  protected ParameterField<String> targetLanguage;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "target_language_version")
  @JsonProperty("target_language_version")
  protected ParameterField<String> targetLanguageVersion;
}
