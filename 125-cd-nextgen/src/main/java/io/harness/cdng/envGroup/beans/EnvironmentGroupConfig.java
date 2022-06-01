/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@RecasterAlias("io.harness.cdng.envGroup.beans.EnvironmentGroupConfig")
@JsonTypeName("environmentGroup")
public class EnvironmentGroupConfig {
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;

  String orgIdentifier;
  String projectIdentifier;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) String description;
  Map<String, String> tags;

  private List<String> envIdentifiers;
}
