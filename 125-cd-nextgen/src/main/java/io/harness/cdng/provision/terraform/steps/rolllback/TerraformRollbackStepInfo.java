/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.steps.rolllback;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.executions.steps.StepSpecTypeConstants.TERRAFORM_ROLLBACK;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.runtime;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName(TERRAFORM_ROLLBACK)
@RecasterAlias("io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStepInfo")
public class TerraformRollbackStepInfo {
  @NotNull String provisionerIdentifier;

  @YamlSchemaTypes(value = {runtime})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;
}
