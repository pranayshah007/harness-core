/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.delegate.task.ecs.EcsInstanceUnitType;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("ecsUpgradeContainerBaseStepInfo")
@FieldNameConstants(innerTypeName = "EcsUpgradeContainerBaseStepInfoKeys")
public class EcsUpgradeContainerBaseStepInfo {
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) EcsInstanceUnitType newServiceInstanceUnit;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) EcsInstanceUnitType downsizeOldServiceInstanceUnit;

  @NotNull
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  ParameterField<Integer> newServiceInstanceCount;

  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.INTEGER_CLASSPATH)
  ParameterField<Integer> downsizeOldServiceInstanceCount;

  @JsonIgnore String ecsServiceSetupFqn;
}
