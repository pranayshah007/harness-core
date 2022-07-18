/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.stages;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.build.BuildStatusUpdateParameter;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure.Type;
import io.harness.beans.yaml.extended.infrastrucutre.RunsOnInfra;
import io.harness.beans.yaml.extended.infrastrucutre.RunsOnInfra.RunOnInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
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
  List<String> stepIdentifiers;
  String childNodeID;

  public static IntegrationStageStepParametersPMS getStepParameters(StageElementConfig stageElementConfig,
      String childNodeID, BuildStatusUpdateParameter buildStatusUpdateParameter, PlanCreationContext ctx) {
    if (stageElementConfig == null) {
      return IntegrationStageStepParametersPMS.builder().childNodeID(childNodeID).build();
    }
    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageElementConfig.getStageType();

    Infrastructure infrastructure = getInfrastructure(stageElementConfig, ctx);

    List<String> stepIdentifiers = getStepIdentifiers(integrationStageConfig);

    return IntegrationStageStepParametersPMS.builder()
        .buildStatusUpdateParameter(buildStatusUpdateParameter)
        .infrastructure(infrastructure)
        .dependencies(integrationStageConfig.getServiceDependencies().getValue())
        .childNodeID(childNodeID)
        .sharedPaths(integrationStageConfig.getSharedPaths())
        .enableCloneRepo(integrationStageConfig.getCloneCodebase())
        .stepIdentifiers(stepIdentifiers)
        .build();
  }

  public static Infrastructure getInfrastructure(StageElementConfig stageElementConfig, PlanCreationContext ctx) {
    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageElementConfig.getStageType();

    Infrastructure infrastructure = integrationStageConfig.getInfrastructure();
    if (infrastructure == null) {
      String runsOn = integrationStageConfig.getRunsOn().getValue();
      if (isEmpty(runsOn)) {
        throw new CIStageExecutionException("Infrastructure or runsOn field is mandatory for execution");
      }

      infrastructure = RunsOnInfra.builder().spec(RunOnInfraSpec.builder().runsOn(runsOn).build()).build();
    } else if (integrationStageConfig.getInfrastructure().getType() == Type.USE_FROM_STAGE) {
      UseFromStageInfraYaml useFromStageInfraYaml = (UseFromStageInfraYaml) integrationStageConfig.getInfrastructure();
      if (useFromStageInfraYaml.getUseFromStage() != null) {
        YamlField yamlField = ctx.getCurrentField();
        String identifier = useFromStageInfraYaml.getUseFromStage();
        IntegrationStageConfig integrationStage = getIntegrationStageConfig(yamlField, identifier);
        infrastructure = integrationStage.getInfrastructure();
      }
    }

    return infrastructure;
  }

  private static IntegrationStageConfig getIntegrationStageConfig(YamlField yamlField, String identifier) {
    try {
      YamlField stageYamlField = PlanCreatorUtils.getStageConfig(yamlField, identifier);
      StageElementConfig stageElementConfig =
          YamlUtils.read(YamlUtils.writeYamlString(stageYamlField), StageElementConfig.class);
      return (IntegrationStageConfig) stageElementConfig.getStageType();

    } catch (Exception ex) {
      throw new CIStageExecutionException(
          "Failed to deserialize IntegrationStage for use from stage identifier: " + identifier, ex);
    }
  }

  private static List<String> getStepIdentifiers(IntegrationStageConfig integrationStageConfig) {
    List<String> stepIdentifiers = new ArrayList<>();
    integrationStageConfig.getExecution().getSteps().forEach(
        executionWrapper -> addStepIdentifier(executionWrapper, stepIdentifiers));
    return stepIdentifiers;
  }

  private static void addStepIdentifier(ExecutionWrapperConfig executionWrapper, List<String> stepIdentifiers) {
    if (executionWrapper != null) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = getStepElementConfig(executionWrapper);
        stepIdentifiers.add(stepElementConfig.getIdentifier());
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapper);
        parallelStepElementConfig.getSections().forEach(section -> addStepIdentifier(section, stepIdentifiers));
      } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
        StepGroupElementConfig stepGroupElementConfig = getStepGroupElementConfig(executionWrapper);
        for (ExecutionWrapperConfig wrapper : stepGroupElementConfig.getSteps()) {
          addStepIdentifier(wrapper, stepIdentifiers);
        }
      } else {
        throw new InvalidRequestException("Only Parallel, StepElement and StepGroup are supported");
      }
    }
  }

  public static StepGroupElementConfig getStepGroupElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  private static StepElementConfig getStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), StepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  private static ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }
}
