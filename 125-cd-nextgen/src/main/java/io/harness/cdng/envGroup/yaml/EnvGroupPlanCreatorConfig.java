/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.yaml;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.envGroup.helper.EnvGroupPlanCreatorConfigVisitorHelper;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

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
@TypeAlias("envGroupPlanCreatorConfig")
@SimpleVisitorHelper(helperClass = EnvGroupPlanCreatorConfigVisitorHelper.class)
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig")
public class EnvGroupPlanCreatorConfig implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @NotNull ParameterField<String> environmentGroupRef;

  String orgIdentifier;
  String projectIdentifier;
  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  Map<String, String> tags;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) String description;

  boolean deployToAll;
  List<EnvironmentPlanCreatorConfig> environmentPlanCreatorConfigs;
}
