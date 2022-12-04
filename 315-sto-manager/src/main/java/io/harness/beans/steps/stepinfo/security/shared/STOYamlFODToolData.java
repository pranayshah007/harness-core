/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo.security.shared;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.codehaus.jackson.annotate.JsonProperty;

@Data
@OwnedBy(STO)
public class STOYamlFODToolData {
  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("app_name")
  protected ParameterField<String> appName;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("owner_id")
  protected ParameterField<String> ownerId;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("loookup_type")
  protected ParameterField<String> loookupType;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("audit_type")
  protected ParameterField<String> auditType;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("scan_type")
  protected ParameterField<String> scanType;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("scan_settings")
  protected ParameterField<String> scanSettings;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("entitlement")
  protected ParameterField<String> entitlement;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("data_center")
  protected ParameterField<String> dataCenter;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("release_name")
  protected ParameterField<String> releaseName;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("target_language")
  protected ParameterField<String> targetLanguage;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = STRING_CLASSPATH)
  @JsonProperty("target_language_version")
  protected ParameterField<String> targetLanguageVersion;
}
