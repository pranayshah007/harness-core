/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.environment.yaml;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.helper.EnvironmentInfraUseFromStageV2VisitorHelper;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@SimpleVisitorHelper(helperClass = EnvironmentInfraUseFromStageV2VisitorHelper.class)
@TypeAlias("environmentInfraUseFromStageV2")
@RecasterAlias("io.harness.cdng.environment.yaml.EnvironmentInfraUseFromStage")
public class EnvironmentInfraUseFromStage {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  // Stage identifier of the stage to select from.
  @ApiModelProperty(required = true)
  @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN)
  @NotNull
  String stage;

  // For Visitor Framework Impl
  String metadata;
}
