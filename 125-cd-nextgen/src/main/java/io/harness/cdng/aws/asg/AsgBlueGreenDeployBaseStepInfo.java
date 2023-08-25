/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.elastigroup.ElastigroupInstances;
import io.harness.cdng.elastigroup.LoadBalancer;
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

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@AllArgsConstructor
@TypeAlias("AsgBlueGreenDeployBaseStepInfo")
public class AsgBlueGreenDeployBaseStepInfo {
  @Deprecated @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> loadBalancer;

  @Deprecated @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> prodListener;

  @Deprecated
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> prodListenerRuleArn;

  @Deprecated @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> stageListener;

  @Deprecated
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  ParameterField<String> stageListenerRuleArn;

  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  @Deprecated
  @YamlSchemaTypes({expression})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  @JsonProperty("useAlreadyRunningInstances")
  ParameterField<Boolean> useAlreadyRunningInstances;

  @ApiModelProperty(dataType = SwaggerConstants.INSTANCES_DEFINITION_YAML_ELASTIGROUP_CONFIGURATION_CLASSPATH)
  ElastigroupInstances instances;

  @ApiModelProperty(dataType = SwaggerConstants.LOAD_BALANCER_CONFIGURATION_CLASSPATH) List<LoadBalancer> loadBalancers;
}
