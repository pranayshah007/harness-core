/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps.stepinfo;
import static io.harness.annotations.dev.HarnessTeam.IACM;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepinfo.IACMStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.beans.ConstructorProperties;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.TypeAlias;

@Data
@JsonTypeName("IACMApproval")
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("iacmApprovalnfo")
@OwnedBy(IACM)
@RecasterAlias("io.harness.beans.steps.stepinfo.IACMApprovalInfo")
@EqualsAndHashCode(callSuper = true)
public class IACMApprovalInfo extends IACMStepInfo {
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String workspace;

  @VariableExpression(skipVariableExpression = true)
  @YamlSchemaTypes(value = {string})
  private ParameterField<Map<String, String>> env;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> image;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.IACM_APPROVAL).build();
  }

  @Builder
  @ConstructorProperties({"identifier", "name", "retry", "settings", "resources", "outputVariables", "runAsUser",
      "privileged", "imagePullPolicy", "env", "image"})
  public IACMApprovalInfo(String identifier, String name, Integer retry, ParameterField<Map<String, String>> env,
      ParameterField<String> image) {
    super.identifier = identifier;
    super.name = name;
    super.retry = Optional.ofNullable(retry).orElse(DEFAULT_RETRY);
    this.env = env;
    this.image = image;
  }
}
