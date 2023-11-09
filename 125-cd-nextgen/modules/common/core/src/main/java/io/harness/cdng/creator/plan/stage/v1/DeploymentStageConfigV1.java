/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage.v1;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.plancreator.customDeployment.v1.StepTemplateRefV1;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Builder
@Data
@TypeAlias("DeploymentStageConfigV1")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PIPELINE})
@SimpleVisitorHelper(helperClass = DeploymentStageVisitorHelperV1.class)
public class DeploymentStageConfigV1 implements Visitable {
  String uuid;
  @JsonProperty("deployment_template") StepTemplateRefV1 deploymentTemplate;
  @JsonProperty("gitops_enabled") Boolean gitOpsEnabled;
  @NotNull @Size(min = 1) List<JsonNode> steps;
  @JsonProperty("rollback_steps") List<JsonNode> rollbackSteps;
  ServiceYamlV2 service;
  ServicesYaml services;
  EnvironmentYamlV2 environment;
  EnvironmentsYaml environments;
  EnvironmentGroupYaml environmentGroup;
  String desc;

  @Override
  public VisitableChildren getChildrenToWalk() {
    List<VisitableChild> children = new ArrayList<>();
    if (service != null) {
      children.add(VisitableChild.builder().value(service).fieldName("service").build());
    }
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
    return VisitableChildren.builder().visitableChildList(children).build();
  }
}
