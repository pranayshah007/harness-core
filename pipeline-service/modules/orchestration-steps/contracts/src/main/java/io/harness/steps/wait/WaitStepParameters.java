/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.wait;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Data
@NoArgsConstructor
@TypeAlias("waitStepParameters")
@OwnedBy(HarnessTeam.CDC)
@RecasterAlias("io.harness.steps.wait.WaitStepParameters")
public class WaitStepParameters implements SpecParameters {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.TIMEOUT_PATTERN)
  @VariableExpression(skipInnerObjectTraversal = true)
  ParameterField<String> duration;
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @Builder(builderMethodName = "infoBuilder")
  public WaitStepParameters(ParameterField<String> duration, String uuid) {
    this.duration = duration;
    this.uuid = uuid;
  }
}
