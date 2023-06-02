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

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(STO)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class STOYamlFODToolData {
  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "app_name") protected ParameterField<String> appName;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "owner_id") protected ParameterField<String> ownerId;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "lookup_type") protected ParameterField<String> lookupType;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "audit_type") protected ParameterField<String> auditType;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "scan_type") protected ParameterField<String> scanType;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "scan_settings") protected ParameterField<String> scanSettings;

  @ApiModelProperty(dataType = STRING_CLASSPATH) protected ParameterField<String> entitlement;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "data_center") protected ParameterField<String> dataCenter;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "release_name") protected ParameterField<String> releaseName;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "target_language")
  protected ParameterField<String> targetLanguage;

  @ApiModelProperty(dataType = STRING_CLASSPATH, name = "target_language_version")
  protected ParameterField<String> targetLanguageVersion;
}
