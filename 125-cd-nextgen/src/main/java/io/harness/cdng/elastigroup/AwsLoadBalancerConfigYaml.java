/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Data
@Builder
@JsonTypeName("AWSLoadBalancerConfig")
@TypeAlias("AwsLoadBalancerConfigYaml")
@RecasterAlias("io.harness.cdng.elastigroup.AwsLoadBalancerConfigYaml")
public class AwsLoadBalancerConfigYaml implements LoadBalancerSpec {
  @NotEmpty @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> loadBalancer;

  @NotEmpty @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> prodListenerPort;

  @NotEmpty @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> prodListenerRuleArn;

  @NotEmpty @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> stageListenerPort;

  @NotEmpty @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> stageListenerRuleArn;

  @Override
  @JsonIgnore
  public LoadBalancerType getType() {
    return LoadBalancerType.AWS_LOAD_BALANCER_CONFIG;
  }
}
