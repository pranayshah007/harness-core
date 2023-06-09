/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.stages;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml.DockerInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml.HostedVmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.registry.Registry;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("integrationStageStepParameters")
@OwnedBy(HarnessTeam.CI)
@RecasterAlias("io.harness.beans.stages.IntegrationStageStepParametersPMS")
public class IntegrationStageStepParametersPMS implements SpecParameters, StepParameters {
  Infrastructure infrastructure;
  List<DependencyElement> dependencies;
  ParameterField<List<String>> sharedPaths;
  ParameterField<Boolean> enableCloneRepo;
  BuildStatusUpdateParameter buildStatusUpdateParameter;
  @Deprecated(since = "moved the usage to runtime as plan creation doesn't have strategy context")
  List<String> stepIdentifiers;
  String childNodeID;
  Caching caching;
  Registry registry;
  CodeBase codeBase;
  TriggerPayload triggerPayload;
  Boolean cloneManually;

  public static IntegrationStageStepParametersPMS getStepParameters(IntegrationStageNode stageNode, String childNodeID,
      BuildStatusUpdateParameter buildStatusUpdateParameter, PlanCreationContext ctx) {
    if (stageNode == null) {
      return IntegrationStageStepParametersPMS.builder().childNodeID(childNodeID).build();
    }
    IntegrationStageConfig integrationStageConfig = stageNode.getIntegrationStageConfig();

    Infrastructure infrastructure = getInfrastructure(stageNode, ctx);

    List<String> stepIdentifiers =
        IntegrationStageUtils.getStepIdentifiers(integrationStageConfig.getExecution().getSteps());

    return IntegrationStageStepParametersPMS.builder()
        .buildStatusUpdateParameter(buildStatusUpdateParameter)
        .infrastructure(infrastructure)
        .dependencies(integrationStageConfig.getServiceDependencies().getValue())
        .childNodeID(childNodeID)
        .sharedPaths(integrationStageConfig.getSharedPaths())
        .enableCloneRepo(integrationStageConfig.getCloneCodebase())
        .stepIdentifiers(stepIdentifiers)
        .caching(getCaching(stageNode))
        .build();
  }

  public static IntegrationStageStepParametersPMS getStepParameters(
      PlanCreationContext ctx, IntegrationStageNode stageNode, CodeBase codeBase, String childNodeID) {
    IntegrationStageStepParametersPMS integrationStageStepParametersPMS =
        getStepParameters(stageNode, childNodeID, null, ctx);
    integrationStageStepParametersPMS.setCodeBase(codeBase);
    integrationStageStepParametersPMS.setTriggerPayload(ctx.getTriggerPayload());
    integrationStageStepParametersPMS.setCloneManually(IntegrationStageUtils.shouldCloneManually(codeBase));
    return integrationStageStepParametersPMS;
  }

  private static Infrastructure getRuntimeInfrastructure(IntegrationStageConfig integrationStageConfig) {
    Runtime runtime = integrationStageConfig.getRuntime();
    if (runtime != null && runtime.getType() == Runtime.Type.DOCKER) {
      return DockerInfraYaml.builder()
          .spec(DockerInfraSpec.builder().platform(integrationStageConfig.getPlatform()).build())
          .build();
    }

    if (runtime != null && runtime.getType() == Runtime.Type.CLOUD) {
      return HostedVmInfraYaml.builder()
          .spec(HostedVmInfraSpec.builder().platform(integrationStageConfig.getPlatform()).build())
          .build();
    }

    throw new CIStageExecutionException(
        "Infrastructure or runtime field with type Cloud or type Docker is mandatory for execution");
  }

  public static Infrastructure getInfrastructure(IntegrationStageNode stageNode, PlanCreationContext ctx) {
    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageNode.getIntegrationStageConfig();

    Infrastructure infrastructure = integrationStageConfig.getInfrastructure();
    if (infrastructure == null) {
      infrastructure = getRuntimeInfrastructure(integrationStageConfig);
    } else if (integrationStageConfig.getInfrastructure().getType() == Type.USE_FROM_STAGE) {
      UseFromStageInfraYaml useFromStageInfraYaml = (UseFromStageInfraYaml) integrationStageConfig.getInfrastructure();
      if (useFromStageInfraYaml.getUseFromStage() != null) {
        YamlField yamlField = ctx.getCurrentField();
        String identifier = useFromStageInfraYaml.getUseFromStage();
        IntegrationStageConfig useFromStage = getIntegrationStageConfig(yamlField, identifier);
        infrastructure = useFromStage.getInfrastructure();
        if (infrastructure == null) {
          infrastructure = getRuntimeInfrastructure(useFromStage);
        }
      }
    }

    return infrastructure;
  }
  public static Caching getCaching(IntegrationStageNode stageNode) {
    IntegrationStageConfig integrationStageConfig = stageNode.getIntegrationStageConfig();
    return integrationStageConfig.getCaching();
  }

  private static IntegrationStageConfig getIntegrationStageConfig(YamlField yamlField, String identifier) {
    try {
      YamlField stageYamlField = PlanCreatorUtils.getStageConfig(yamlField, identifier);
      IntegrationStageNode stageNode =
          YamlUtils.read(YamlUtils.writeYamlString(stageYamlField), IntegrationStageNode.class);
      return (IntegrationStageConfig) stageNode.getStageInfoConfig();

    } catch (Exception ex) {
      throw new CIStageExecutionException(
          "Failed to deserialize IntegrationStage for use from stage identifier: " + identifier, ex);
    }
  }
}
