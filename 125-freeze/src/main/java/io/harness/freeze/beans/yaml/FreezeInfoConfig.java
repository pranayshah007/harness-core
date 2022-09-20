/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans.yaml;

import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeNotifications;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.pms.yaml.ParameterField;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;

@Data
public class FreezeInfoConfig {
  @JsonProperty("__uuid")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> description;
  Map<String, String> tags;

  String orgIdentifier;
  String projectIdentifier;

  @JsonProperty("status") FreezeStatus active;

  @JsonProperty("windows") List<FreezeWindow> windows;

  @JsonProperty("entityConfigs") List<FreezeEntityRule> rules;

  FreezeNotifications notifications;
}
