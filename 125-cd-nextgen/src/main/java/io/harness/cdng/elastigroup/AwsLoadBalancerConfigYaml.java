/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.YamlSchemaTypes;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Wither;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.expression;

@OwnedBy(CDP)
@Value
@Data
@Builder
@JsonTypeName("AWSLoadBalancerConfig")
@TypeAlias("AwsLoadBalancerConfigYaml")
@RecasterAlias("io.harness.cdng.elastigroup.AwsLoadBalancerConfigYaml")
public class AwsLoadBalancerConfigYaml implements LoadBalancerSpec {

  @NotEmpty @YamlSchemaTypes({expression}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> loadBalancerArn;

  @NotEmpty @YamlSchemaTypes({expression}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> prodListenerPort;

  @NotEmpty @YamlSchemaTypes({expression}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> prodListenerRuleArn;

  @NotEmpty @YamlSchemaTypes({expression}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> stageListenerPort;

  @NotEmpty @YamlSchemaTypes({expression}) @ApiModelProperty(dataType = STRING_CLASSPATH) ParameterField<String> stageListenerRuleArn;

  @Override
  @JsonIgnore
  public LoadBalancerType getType() {
    return LoadBalancerType.AWS_LOAD_BALANCER_CONFIG;
  }
}
