/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.deploymentmetadata.GoogleCloudFunctionDeploymentMetaData;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.plancreator.customDeployment.StepTemplateRef;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.OneOfField;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;
import io.harness.yaml.core.VariableExpression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonTypeName("Deployment")
@TypeAlias("deploymentStageConfig")
@OneOfField(fields = {"service", "services", "serviceConfig"})
@OneOfField(fields = {"environment", "environments", "environmentGroup", "infrastructure"})
@SimpleVisitorHelper(helperClass = DeploymentStageVisitorHelper.class)
public class DeploymentStageConfig implements StageInfoConfig, Visitable {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  ServiceConfig serviceConfig;

  // For new service yaml
  // skipping variable creation from framework since these are supported through outcomes
  @VariableExpression(skipVariableExpression = true) ServiceYamlV2 service;
  // For multiple services support
  @VariableExpression(skipVariableExpression = true) ServicesYaml services;

  ServiceDefinitionType deploymentType;
  Boolean gitOpsEnabled;

  @VariableExpression(skipVariableExpression = true) StepTemplateRef customDeploymentRef;
  // New Environment Yaml
  // skipping variable creation from framework since these are supported through outcomes
  @VariableExpression(skipVariableExpression = true) EnvironmentYamlV2 environment;
  // New Multi Environment Yaml
  // skipping variable creation from framework since these are supported through outcomes
  @VariableExpression(skipVariableExpression = true) EnvironmentsYaml environments;

  // Environment Group yaml
  // todo: add expressions from env group outcomes
  @VariableExpression(skipVariableExpression = true) EnvironmentGroupYaml environmentGroup;

  PipelineInfrastructure infrastructure;
  @NotNull @VariableExpression(skipVariableExpression = true) ExecutionElementConfig execution;

  GoogleCloudFunctionDeploymentMetaData deploymentMetadata;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Override
  public VisitableChildren getChildrenToWalk() {
    List<VisitableChild> children = new ArrayList<>();
    children.add(VisitableChild.builder().value(serviceConfig).fieldName("serviceConfig").build());
    if (service != null) {
      children.add(VisitableChild.builder().value(service).fieldName("service").build());
    }
    children.add(VisitableChild.builder().value(infrastructure).fieldName("infrastructure").build());
    if (environment != null) {
      children.add(VisitableChild.builder().value(environment).fieldName("environment").build());
    }
    if (environmentGroup != null) {
      children.add(VisitableChild.builder().value(environmentGroup).fieldName("environmentGroup").build());
    }
    if (environments != null) {
      children.add(VisitableChild.builder().value(environments).fieldName("environments").build());
    }
    if (services != null) {
      children.add(VisitableChild.builder().value(services).fieldName("services").build());
    }
    if (deploymentMetadata != null) {
      children.add(VisitableChild.builder().value(deploymentMetadata).fieldName("deploymentMetadata").build());
    }
    return VisitableChildren.builder().visitableChildList(children).build();
  }

  public boolean getGitOpsEnabled() {
    return gitOpsEnabled == Boolean.TRUE;
  }
}
