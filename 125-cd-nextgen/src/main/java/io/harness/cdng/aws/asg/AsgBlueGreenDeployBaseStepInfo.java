/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.SwaggerConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("AsgBlueGreenDeployBaseStepInfo")
public class AsgBlueGreenDeployBaseStepInfo {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Deprecated ParameterField<String> loadBalancer;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Deprecated ParameterField<String> prodListener;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Deprecated
  ParameterField<String> prodListenerRuleArn;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Deprecated ParameterField<String> stageListener;

  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @Deprecated
  ParameterField<String> stageListenerRuleArn;

  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @JsonProperty("useAlreadyRunningInstances")
  @Deprecated
  ParameterField<Boolean> useAlreadyRunningInstances;

  @ApiModelProperty(dataType = SwaggerConstants.ASG_INSTANCES_CLASSPATH) AsgInstances instances;

  @ApiModelProperty(dataType = SwaggerConstants.ASG_LOAD_BALANCER_CONFIGURATION_CLASSPATH)
  List<AwsAsgLoadBalancerConfigYaml> loadBalancers;
}
