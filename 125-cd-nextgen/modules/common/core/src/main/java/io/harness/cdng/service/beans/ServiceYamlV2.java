/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.beans;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtimeEmptyStringAllowed;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Map;
import javax.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@Data
@Builder
@SimpleVisitorHelper(helperClass = ServiceEntityVisitorHelperV2.class)
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.service.beans.ServiceYamlV2")
@FieldNameConstants(innerTypeName = "keys")
public class ServiceYamlV2 implements Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  // For New Service Yaml
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Pattern(regexp = NGRegexValidatorConstants.NON_EMPTY_STRING_PATTERN)
  private ParameterField<String> serviceRef;

  private String gitBranch;

  @VariableExpression(skipVariableExpression = true) private ServiceUseFromStageV2 useFromStage;

  @ApiModelProperty(dataType = SwaggerConstants.JSON_NODE_CLASSPATH)
  @YamlSchemaTypes({runtimeEmptyStringAllowed})
  ParameterField<Map<String, Object>> serviceInputs;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;
}
