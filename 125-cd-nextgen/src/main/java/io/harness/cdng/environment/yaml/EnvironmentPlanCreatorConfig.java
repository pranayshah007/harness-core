/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.environment.helper.EnvironmentPlanCreatorConfigVisitorHelper;
import io.harness.cdng.infra.yaml.InfrastructurePlanCreatorConfig;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.variables.NGServiceOverrides;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("environmentPlanCreatorConfig")
@SimpleVisitorHelper(helperClass = EnvironmentPlanCreatorConfigVisitorHelper.class)
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig")
public class EnvironmentPlanCreatorConfig implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull ParameterField<String> environmentRef;

  // Environment Basic Info
  String orgIdentifier;
  String projectIdentifier;
  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  Map<String, String> tags;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) String description;
  @ApiModelProperty(required = true) EnvironmentType type;
  List<NGVariable> variables;
  List<NGServiceOverrides> serviceOverrides;

  // linked Infra Info
  List<InfrastructurePlanCreatorConfig> infrastructureDefinitions;
  List<String> clusterRefs;
}
