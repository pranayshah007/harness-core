/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameterWithDefaultValue;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_BUILD_EVENT;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.ci.commonconstants.BuildEnvironmentConstants.DRONE_TAG;
import static io.harness.ci.commonconstants.CIExecutionConstants.DEFAULT_CONTAINER_CPU_POV;
import static io.harness.ci.commonconstants.CIExecutionConstants.DEFAULT_CONTAINER_MEM_POV;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_SSL_NO_VERIFY;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.ci.integrationstage.IntegrationStageUtils.BRANCH_EXPRESSION;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.ci.utils.QuantityUtils;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.filters.WithConnectorRef;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.impl.TagBuildSpec;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class K8InitializeStepUtils {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private CodebaseUtils codebaseUtils;
  private final String AXA_ACCOUNT_ID = "UVxMDMhNQxOCvroqqImWdQ";

  public List<ContainerDefinitionInfo> createStepContainerDefinitions(List<ExecutionWrapperConfig> steps,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, String accountId,
      OSType os, Ambiance ambiance) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    if (steps == null) {
      return containerDefinitionInfos;
    }

    Integer stageMemoryRequest = getStageMemoryRequest(steps, accountId);
    Integer stageCpuRequest = getStageCpuRequest(steps, accountId);

    int stepIndex = 0;
    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
        stepIndex++;
        if (stepElementConfig.getTimeout() != null && stepElementConfig.getTimeout().isExpression()) {
          throw new InvalidRequestException(
              "Timeout field must be resolved in step: " + stepElementConfig.getIdentifier());
        }

        ContainerResource containerResource = getContainerResource(stepElementConfig);
        Integer extraMemoryPerStep = Math.max(
            0, stageMemoryRequest - getContainerMemoryLimit(containerResource, "stepType", "stepId", accountId));
        Integer extraCPUPerStep =
            Math.max(0, stageCpuRequest - getContainerCpuLimit(containerResource, "stepType", "stepId", accountId));
        ContainerDefinitionInfo containerDefinitionInfo =
            createStepContainerDefinition(stepElementConfig, integrationStage, ciExecutionArgs, portFinder, stepIndex,
                accountId, os, ambiance, extraMemoryPerStep, extraCPUPerStep);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        ParallelStepElementConfig parallelStepElementConfig =
            IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
        if (isNotEmpty(parallelStepElementConfig.getSections())) {
          Integer extraMemoryPerStep =
              calculateExtraMemoryForParallelStep(parallelStepElementConfig, accountId, stageMemoryRequest);
          Integer extraCPUPerStep =
              calculateExtraCPUForParallelStep(parallelStepElementConfig, accountId, stageCpuRequest);
          for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
            if (executionWrapperInParallel.getStep() == null || executionWrapperInParallel.getStep().isNull()) {
              continue;
            }

            stepIndex++;
            StepElementConfig stepElementConfig =
                IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
            ContainerDefinitionInfo containerDefinitionInfo =
                createStepContainerDefinition(stepElementConfig, integrationStage, ciExecutionArgs, portFinder,
                    stepIndex, accountId, os, ambiance, extraMemoryPerStep, extraCPUPerStep);
            if (containerDefinitionInfo != null) {
              containerDefinitionInfos.add(containerDefinitionInfo);
            }
          }
        }
      }
    }
    return containerDefinitionInfos;
  }

  public List<ContainerDefinitionInfo> createStepContainerDefinitionsStepGroupWithFF(List<ExecutionWrapperConfig> steps,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, String accountId,
      OSType os, Ambiance ambiance, int stepIndex) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    if (steps == null) {
      return containerDefinitionInfos;
    }

    Integer stageMemoryRequest = getStageMemoryRequest(steps, accountId);
    Integer stageCpuRequest = getStageCpuRequest(steps, accountId);

    for (ExecutionWrapperConfig executionWrapper : steps) {
      if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
        stepIndex++;
        ContainerDefinitionInfo containerDefinitionInfo = handleSingleStep(executionWrapper, integrationStage,
            ciExecutionArgs, portFinder, accountId, os, ambiance, stageMemoryRequest, stageCpuRequest, stepIndex,  null);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
        Integer extraMemory = calculateExtraMemory(executionWrapper, accountId, stageMemoryRequest);
        Integer extraCPU = calculateExtraCPU(executionWrapper, accountId, stageCpuRequest);
        List<ContainerDefinitionInfo> parallelDefinitionInfos = handleParallelStep(executionWrapper, integrationStage,
            ciExecutionArgs, portFinder, accountId, os, ambiance, extraMemory, extraCPU, stepIndex, null);
        if (parallelDefinitionInfos != null) {
          stepIndex += parallelDefinitionInfos.size();
          if (parallelDefinitionInfos.size() > 0) {
            containerDefinitionInfos.addAll(parallelDefinitionInfos);
          }
        }
      } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
        List<ContainerDefinitionInfo> stepGroupDefinitionInfos = handleStepGroup(executionWrapper, integrationStage,
            ciExecutionArgs, portFinder, accountId, os, ambiance, stageMemoryRequest, stageCpuRequest, stepIndex);
        if (stepGroupDefinitionInfos != null) {
          stepIndex += stepGroupDefinitionInfos.size();
          if (stepGroupDefinitionInfos.size() > 0) {
            containerDefinitionInfos.addAll(stepGroupDefinitionInfos);
          }
        }
      } else {
        throw new InvalidRequestException("Only Parallel, StepElement and StepGroup are supported");
      }
    }
    return containerDefinitionInfos;
  }

  private ContainerDefinitionInfo handleSingleStep(ExecutionWrapperConfig executionWrapper,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, String accountId,
      OSType os, Ambiance ambiance, int maxAllocatableMemoryRequest, int maxAllocatableCpuRequest, int stepIndex,
      String stepGroupIdOfParent) {
    StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
    if (Strings.isNotBlank(stepGroupIdOfParent)) {
      stepElementConfig.setIdentifier(stepGroupIdOfParent + "_" + stepElementConfig.getIdentifier());
    }

    Integer extraMemoryPerStep = calculateExtraMemory(executionWrapper, accountId, maxAllocatableMemoryRequest);
    Integer extraCPUPerStep = calculateExtraCPU(executionWrapper, accountId, maxAllocatableCpuRequest);
    return createStepContainerDefinition(stepElementConfig, integrationStage, ciExecutionArgs, portFinder, stepIndex,
        accountId, os, ambiance, extraMemoryPerStep, extraCPUPerStep);
  }

  private List<ContainerDefinitionInfo> handleStepGroup(ExecutionWrapperConfig executionWrapper,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, String accountId,
      OSType os, Ambiance ambiance, int maxAllocatableMemoryRequest, int maxAllocatableCpuRequest, int stepIndex) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    StepGroupElementConfig stepGroupElementConfig = IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
    if (isEmpty(stepGroupElementConfig.getSteps())) {
      return containerDefinitionInfos;
    }

    for (ExecutionWrapperConfig step : stepGroupElementConfig.getSteps()) {
      if (step.getStep() != null && !step.getStep().isNull()) {
        stepIndex++;
        ContainerDefinitionInfo containerDefinitionInfo = handleSingleStep(step, integrationStage, ciExecutionArgs,
            portFinder, accountId, os, ambiance,  maxAllocatableMemoryRequest, maxAllocatableCpuRequest, stepIndex,
            stepGroupElementConfig.getIdentifier());
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (step.getParallel() != null && !step.getParallel().isNull()) {
        int extraMemory = calculateExtraMemory(step, accountId, maxAllocatableMemoryRequest);
        int extraCpu = calculateExtraCPU(step, accountId, maxAllocatableCpuRequest);
        List<ContainerDefinitionInfo> parallelStepDefinitionInfos =
            handleParallelStep(step, integrationStage, ciExecutionArgs, portFinder, accountId, os, ambiance,
               extraMemory, extraCpu, stepIndex, stepGroupElementConfig.getIdentifier());
        if (parallelStepDefinitionInfos != null) {
          stepIndex += parallelStepDefinitionInfos.size();
          if (parallelStepDefinitionInfos.size() > 0) {
            containerDefinitionInfos.addAll(parallelStepDefinitionInfos);
          }
        }
      }
    }
    return containerDefinitionInfos;
  }

  private List<ContainerDefinitionInfo> handleParallelStep(ExecutionWrapperConfig executionWrapper,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, String accountId,
      OSType os, Ambiance ambiance, int extraMemory, int extraCPU, int stepIndex, String stepGroupIdOfParent) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    ParallelStepElementConfig parallelStepElementConfig =
        IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
    if (isEmpty(parallelStepElementConfig.getSections())) {
      return containerDefinitionInfos;
    }

    int steps = parallelStepElementConfig.getSections().size();
    Integer extraMemoryPerStep = extraMemory / steps;
    Integer extraCPUPerStep = extraCPU / steps;

    for (ExecutionWrapperConfig executionWrapperInParallel : parallelStepElementConfig.getSections()) {
      if (executionWrapperInParallel.getStep() != null && !executionWrapperInParallel.getStep().isNull()) {
        StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapperInParallel);
        stepIndex++;
        ContainerDefinitionInfo containerDefinitionInfo =
            handleSingleStep(executionWrapperInParallel, integrationStage, ciExecutionArgs, portFinder, accountId, os,
                ambiance, extraMemoryPerStep + getExecutionWrapperMemoryRequest(executionWrapperInParallel, accountId),
                extraCPUPerStep + getExecutionWrapperCpuRequest(executionWrapperInParallel, accountId), stepIndex,
                stepGroupIdOfParent);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
      } else if (executionWrapperInParallel.getStepGroup() != null
          && !executionWrapperInParallel.getStepGroup().isNull()) {
        List<ContainerDefinitionInfo> stepGroupDefinitionInfos =
            handleStepGroup(executionWrapperInParallel, integrationStage, ciExecutionArgs, portFinder, accountId, os,
                ambiance, extraMemoryPerStep + getExecutionWrapperMemoryRequest(executionWrapperInParallel, accountId),
                extraCPUPerStep + getExecutionWrapperCpuRequest(executionWrapperInParallel, accountId), stepIndex);
        if (stepGroupDefinitionInfos != null) {
          stepIndex += stepGroupDefinitionInfos.size();
          if (stepGroupDefinitionInfos.size() > 0) {
            containerDefinitionInfos.addAll(stepGroupDefinitionInfos);
          }
        }
      }
    }

    return containerDefinitionInfos;
  }

  private ContainerDefinitionInfo createStepContainerDefinition(StepElementConfig stepElement,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String accountId, OSType os, Ambiance ambiance, Integer extraMemoryPerStep, Integer extraCPUPerStep) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return null;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    validateStepType(ciStepInfo.getNonYamlInfo().getStepInfoType(), os);

    long timeout = TimeoutUtils.getTimeoutInSeconds(stepElement.getTimeout(), ciStepInfo.getDefaultTimeout());
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return createRunStepContainerDefinition((RunStepInfo) ciStepInfo, integrationStage, ciExecutionArgs, portFinder,
            stepIndex, stepElement.getIdentifier(), stepElement.getName(), accountId, os, extraMemoryPerStep,
            extraCPUPerStep);
      case DOCKER:
      case ECR:
      case GCR:
      case SAVE_CACHE_S3:
      case RESTORE_CACHE_S3:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_GCS:
      case SECURITY:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_S3:
      case UPLOAD_GCS:
        return createPluginCompatibleStepContainerDefinition((PluginCompatibleStep) ciStepInfo, integrationStage,
            ciExecutionArgs, portFinder, stepIndex, stepElement.getIdentifier(), stepElement.getName(),
            stepElement.getType(), timeout, accountId, os, extraMemoryPerStep, extraCPUPerStep);
      case GIT_CLONE:
        return createGitCloneStepContainerDefinition((GitCloneStepInfo) ciStepInfo, integrationStage, ciExecutionArgs,
                portFinder, stepIndex, stepElement.getIdentifier(), stepElement.getName(), accountId, os, ambiance,
                extraMemoryPerStep, extraCPUPerStep);
      case PLUGIN:
        return createPluginStepContainerDefinition((PluginStepInfo) ciStepInfo, integrationStage, ciExecutionArgs,
            portFinder, stepIndex, stepElement.getIdentifier(), stepElement.getName(), accountId, os,
            extraMemoryPerStep, extraCPUPerStep);
      case RUN_TESTS:
        return createRunTestsStepContainerDefinition((RunTestsStepInfo) ciStepInfo, integrationStage, ciExecutionArgs,
            portFinder, stepIndex, stepElement.getIdentifier(), accountId, os, extraMemoryPerStep, extraCPUPerStep);
      default:
        return null;
    }
  }

  public void validateStepType(CIStepInfoType stepType, OSType os) {
    if (os != OSType.Windows) {
      return;
    }

    switch (stepType) {
      case DOCKER:
      case ECR:
      case GCR:
        throw new CIStageExecutionException(format("%s step not allowed in windows kubernetes builds", stepType));
      default:
        return;
    }
  }

  private ContainerDefinitionInfo createPluginCompatibleStepContainerDefinition(PluginCompatibleStep stepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String stepName, String stepType, long timeout, String accountId, OSType os,
      Integer extraMemoryPerStep, Integer extraCPUPerStep) {
    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(getEnvVariables(integrationStage));
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    envVarMap.putAll(
        PluginSettingUtils.getPluginCompatibleEnvVariables(stepInfo, identifier, timeout, StageInfraDetails.Type.K8));
    Integer runAsUser = resolveIntegerParameter(stepInfo.getRunAsUser(), null);

    Boolean privileged = null;
    if (CIStepInfoUtils.getPrivilegedMode(stepInfo) != null) {
      privileged = CIStepInfoUtils.getPrivilegedMode(stepInfo).getValue();
    }
    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(IntegrationStageUtils.getImageInfo(CIStepInfoUtils.getPluginCustomStepImage(
                    stepInfo, ciExecutionConfigService, StageInfraDetails.Type.K8, accountId)))
                .build())
        .isHarnessManagedImage(true)
        .containerResourceParams(getStepContainerResource(
            stepInfo.getResources(), stepType, identifier, accountId, extraMemoryPerStep, extraCPUPerStep))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepIdentifier(identifier)
        .stepName(stepName)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(CIStepInfoUtils.getImagePullPolicy(stepInfo)))
        .privileged(privileged)
        .runAsUser(runAsUser)
        .build();
  }

  private ContainerDefinitionInfo createRunStepContainerDefinition(RunStepInfo runStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String name, String accountId, OSType os, Integer extraMemoryPerStep,
      Integer extraCPUPerStep) {
    if (runStepInfo.getImage() == null) {
      throw new CIStageExecutionException("image can't be empty in k8s infrastructure");
    }

    if (runStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty in k8s infrastructure");
    }

    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    Map<String, String> envvars =
        resolveMapParameter("envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = resolveIntegerParameter(runStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(resolveStringParameter(
                                       "Image", "Run", identifier, runStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(getStepContainerResource(
            runStepInfo.getResources(), "Run", identifier, accountId, extraMemoryPerStep, extraCPUPerStep))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.RUN)
        .stepName(name)
        .privileged(runStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(runStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerDefinitionInfo createRunTestsStepContainerDefinition(RunTestsStepInfo runTestsStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String accountId, OSType os, Integer extraMemoryPerStep, Integer extraCPUPerStep) {
    Integer port = portFinder.getNextPort();

    if (runTestsStepInfo.getImage() == null) {
      throw new CIStageExecutionException("image can't be empty in k8s infrastructure");
    }

    if (runTestsStepInfo.getConnectorRef() == null) {
      throw new CIStageExecutionException("connector ref can't be empty in k8s infrastructure");
    }

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> stepEnvVars = new HashMap<>();
    stepEnvVars.putAll(getEnvVariables(integrationStage));
    stepEnvVars.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    Map<String, String> envvars =
        resolveMapParameter("envVariables", "RunTests", identifier, runTestsStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = resolveIntegerParameter(runTestsStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(resolveStringParameter(
                                       "Image", "RunTest", identifier, runTestsStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "RunTest", identifier, runTestsStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(getStepContainerResource(
            runTestsStepInfo.getResources(), "RunTests", identifier, accountId, extraMemoryPerStep, extraCPUPerStep))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.TEST_INTELLIGENCE)
        .privileged(runTestsStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(runTestsStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerDefinitionInfo createGitCloneStepContainerDefinition(GitCloneStepInfo gitCloneStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String name, String accountId, OSType os, Ambiance ambiance, Integer extraMemoryPerStep,
      Integer extraCPUPerStep) {

    //Create a PluginStepInfo from the GitCloneStepInfo
    PluginStepInfo pluginStepInfo = createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, accountId, os);

    //Get the Git Connector
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(ngAccess,
            pluginStepInfo.getConnectorRef().getValue());

    //Set the Git Connector Reference environment variables
    Map<String, String> gitEnvVars = codebaseUtils.getGitEnvVariables(gitConnector,
            gitCloneStepInfo.getRepoName().getValue());
    pluginStepInfo.getEnvVariables().putAll(gitEnvVars);

    String cloneDirectoryString = RunTimeInputHandler.resolveStringParameter("cloneDirectory", name,
            identifier, gitCloneStepInfo.getCloneDirectory(), false);
    if(isNotEmpty(cloneDirectoryString)) {
      pluginStepInfo.getEnvVariables().put(HARNESS_WORKSPACE, cloneDirectoryString);
    }

    //TODO: not sure if this is the best way to do this. Would GitCloneStep ever need the other information from ExecutionSource?
    //Don't use the ciExecutionArgs ExecutionSource (contains codebase build info that overwrites branch/tag env variables
    ciExecutionArgs.setExecutionSource(null);

    final ContainerDefinitionInfo pluginStepContainerDefinition = createPluginStepContainerDefinition(pluginStepInfo, integrationStage, ciExecutionArgs,
            portFinder, stepIndex, identifier, name, accountId, os,
            extraMemoryPerStep, extraCPUPerStep);
    pluginStepContainerDefinition.setGitConnector(gitConnector);
    return pluginStepContainerDefinition;
  }

  private ContainerDefinitionInfo createPluginStepContainerDefinition(PluginStepInfo pluginStepInfo,
      StageElementConfig integrationStage, CIExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String name, String accountId, OSType os, Integer extraMemoryPerStep,
      Integer extraCPUPerStep) {
    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.putAll(getEnvVariables(integrationStage));
    envVarMap.putAll(BuildEnvironmentUtils.getBuildEnvironmentVariables(ciExecutionArgs));
    if (!isEmpty(pluginStepInfo.getEnvVariables())) {
      envVarMap.putAll(pluginStepInfo.getEnvVariables());
    }

    Integer runAsUser = resolveIntegerParameter(pluginStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .stepIdentifier(identifier)
        .secretVariables(getSecretVariables(integrationStage))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(resolveStringParameter(
                                       "Image", "Plugin", identifier, pluginStepInfo.getImage(), true)))
                                   .connectorIdentifier(resolveStringParameter(
                                       "connectorRef", "Plugin", identifier, pluginStepInfo.getConnectorRef(), true))
                                   .build())
        .containerResourceParams(getStepContainerResource(
            pluginStepInfo.getResources(), "Plugin", identifier, accountId, extraMemoryPerStep, extraCPUPerStep))
        .isHarnessManagedImage(pluginStepInfo.isHarnessManagedImage())
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepName(name)
        .privileged(pluginStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(pluginStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerResourceParams getStepContainerResource(ContainerResource resource, String stepType, String stepId,
      String accountId, Integer extraMemoryPerStep, Integer extraCPUPerStep) {
    Integer cpuLimit;
    Integer memoryLimit;

    if (featureFlagService.isEnabled(FeatureName.CI_DISABLE_RESOURCE_OPTIMIZATION, accountId)
        || accountId.equals(AXA_ACCOUNT_ID)) {
      cpuLimit = getContainerCpuLimit(resource, stepType, stepId, accountId);
      memoryLimit = getContainerMemoryLimit(resource, stepType, stepId, accountId);
    } else {
      cpuLimit = getContainerCpuLimit(resource, stepType, stepId, accountId) + extraCPUPerStep;
      memoryLimit = getContainerMemoryLimit(resource, stepType, stepId, accountId) + extraMemoryPerStep;
    }

    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceLimitMilliCpu(cpuLimit)
        .resourceLimitMemoryMiB(memoryLimit)
        .build();
  }

  private Map<String, String> getEnvVariables(StageElementConfig stageElementConfig) {
    if (isEmpty(stageElementConfig.getVariables())) {
      return Collections.emptyMap();
    }

    return stageElementConfig.getVariables()
        .stream()
        .filter(customVariables -> customVariables.getType() == NGVariableType.STRING)
        .map(customVariable -> (StringNGVariable) customVariable)
        .collect(Collectors.toMap(ngVariable
            -> ngVariable.getName(),
            ngVariable
            -> resolveStringParameterWithDefaultValue("variableValue", "stage", stageElementConfig.getIdentifier(),
                ngVariable.getValue(), false, ngVariable.getDefaultValue())));
  }

  private List<SecretNGVariable> getSecretVariables(StageElementConfig stageElementConfig) {
    if (isEmpty(stageElementConfig.getVariables())) {
      return Collections.emptyList();
    }

    return stageElementConfig.getVariables()
        .stream()
        .filter(variable -> variable.getType() == NGVariableType.SECRET)
        .map(customVariable -> (SecretNGVariable) customVariable)
        .collect(Collectors.toList());
  }

  public Integer getStageMemoryRequest(List<ExecutionWrapperConfig> steps, String accountId) {
    Integer stageMemoryRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(step, accountId);
      stageMemoryRequest = Math.max(stageMemoryRequest, executionWrapperMemoryRequest);
    }
    return stageMemoryRequest;
  }

  private Integer getExecutionWrapperMemoryRequest(ExecutionWrapperConfig executionWrapper, String accountId) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperMemoryRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
      executionWrapperMemoryRequest = getStepMemoryLimit(stepElementConfig, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallel = IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallel.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallel.getSections()) {
          executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper, accountId);
        }
      }
    } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
      StepGroupElementConfig stepGroupElementConfig = IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
      for (ExecutionWrapperConfig wrapper : stepGroupElementConfig.getSteps()) {
        Integer wrapperMemoryRequest = getExecutionWrapperMemoryRequest(wrapper, accountId);
        executionWrapperMemoryRequest = Math.max(executionWrapperMemoryRequest, wrapperMemoryRequest);
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement and StepGroup are supported");
    }

    return executionWrapperMemoryRequest;
  }

  private Integer getStepMemoryLimit(StepElementConfig stepElement, String accountId) {
    Integer zeroMemory = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroMemory;
    }

    ContainerResource containerResource = getStepResources((CIStepInfo) stepElement.getStepSpecType());
    return getContainerMemoryLimit(containerResource, stepElement.getType(), stepElement.getIdentifier(), accountId);
  }

  private Integer getContainerMemoryLimit(
      ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer memoryLimit = ciExecutionConfigService.getCiExecutionServiceConfig().getDefaultMemoryLimit();

    if (featureFlagService.isEnabled(FeatureName.CI_INCREASE_DEFAULT_RESOURCES, accountID)) {
      log.info("Increase default resources FF is enabled for accountID: {}", accountID);
      memoryLimit = DEFAULT_CONTAINER_MEM_POV;
    }

    if (resource != null && resource.getLimits() != null && resource.getLimits().getMemory() != null) {
      String memoryLimitMemoryQuantity =
          resolveStringParameter("memory", stepType, stepId, resource.getLimits().getMemory(), false);
      if (isNotEmpty(memoryLimitMemoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryLimitMemoryQuantity)) {
        memoryLimit = QuantityUtils.getStorageQuantityValueInUnit(memoryLimitMemoryQuantity, StorageQuantityUnit.Mi);
      }
    }
    return memoryLimit;
  }

  public Integer getStageCpuRequest(List<ExecutionWrapperConfig> steps, String accountId) {
    Integer stageCpuRequest = 0;
    for (ExecutionWrapperConfig step : steps) {
      Integer executionWrapperCpuRequest = getExecutionWrapperCpuRequest(step, accountId);
      stageCpuRequest = Math.max(stageCpuRequest, executionWrapperCpuRequest);
    }
    return stageCpuRequest;
  }

  private Integer getExecutionWrapperCpuRequest(ExecutionWrapperConfig executionWrapper, String accountId) {
    if (executionWrapper == null) {
      return 0;
    }

    Integer executionWrapperCpuRequest = 0;
    if (executionWrapper.getStep() != null && !executionWrapper.getStep().isNull()) {
      StepElementConfig stepElementConfig = IntegrationStageUtils.getStepElementConfig(executionWrapper);
      executionWrapperCpuRequest = getStepCpuLimit(stepElementConfig, accountId);
    } else if (executionWrapper.getParallel() != null && !executionWrapper.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElement =
          IntegrationStageUtils.getParallelStepElementConfig(executionWrapper);
      if (isNotEmpty(parallelStepElement.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallelStepElement.getSections()) {
          executionWrapperCpuRequest += getExecutionWrapperCpuRequest(wrapper, accountId);
        }
      }
    } else if (executionWrapper.getStepGroup() != null && !executionWrapper.getStepGroup().isNull()) {
      StepGroupElementConfig stepGroupElementConfig = IntegrationStageUtils.getStepGroupElementConfig(executionWrapper);
      for (ExecutionWrapperConfig wrapper : stepGroupElementConfig.getSteps()) {
        Integer stepCpuRequest = getExecutionWrapperCpuRequest(wrapper, accountId);
        executionWrapperCpuRequest = Math.max(executionWrapperCpuRequest, stepCpuRequest);
      }
    } else {
      throw new InvalidRequestException("Only Parallel, StepElement and StepGroup are supported");
    }
    return executionWrapperCpuRequest;
  }

  private Integer getStepCpuLimit(StepElementConfig stepElement, String accountId) {
    Integer zeroCpu = 0;
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return zeroCpu;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return getContainerCpuLimit(
            ((RunStepInfo) ciStepInfo).getResources(), stepElement.getType(), stepElement.getIdentifier(), accountId);
      case PLUGIN:
        return getContainerCpuLimit(((PluginStepInfo) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      case RUN_TESTS:
        return getContainerCpuLimit(((RunTestsStepInfo) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      case GCR:
      case ECR:
      case DOCKER:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case RESTORE_CACHE_GCS:
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case SAVE_CACHE_GCS:
      case SECURITY:
      case GIT_CLONE:
        return getContainerCpuLimit(((PluginCompatibleStep) ciStepInfo).getResources(), stepElement.getType(),
            stepElement.getIdentifier(), accountId);
      default:
        return zeroCpu;
    }
  }

  private Integer getContainerCpuLimit(ContainerResource resource, String stepType, String stepId, String accountID) {
    Integer cpuLimit = ciExecutionConfigService.getCiExecutionServiceConfig().getDefaultCPULimit();

    if (featureFlagService.isEnabled(FeatureName.CI_INCREASE_DEFAULT_RESOURCES, accountID)) {
      log.info("Increase default resources FF is enabled for accountID: {}", accountID);
      cpuLimit = DEFAULT_CONTAINER_CPU_POV;
    }

    if (resource != null && resource.getLimits() != null && resource.getLimits().getCpu() != null) {
      String cpuLimitQuantity = resolveStringParameter("cpu", stepType, stepId, resource.getLimits().getCpu(), false);
      if (isNotEmpty(cpuLimitQuantity) && !UNRESOLVED_PARAMETER.equals(cpuLimitQuantity)) {
        cpuLimit = QuantityUtils.getCpuQuantityValueInUnit(cpuLimitQuantity, DecimalQuantityUnit.m);
      }
    }
    return cpuLimit;
  }

  private ContainerResource getStepResources(CIStepInfo ciStepInfo) {
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return ((RunStepInfo) ciStepInfo).getResources();
      case PLUGIN:
        return ((PluginStepInfo) ciStepInfo).getResources();
      case RUN_TESTS:
        return ((RunTestsStepInfo) ciStepInfo).getResources();
      case GCR:
      case ECR:
      case DOCKER:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case RESTORE_CACHE_GCS:
      case RESTORE_CACHE_S3:
      case SAVE_CACHE_S3:
      case SAVE_CACHE_GCS:
      case SECURITY:
      case GIT_CLONE:
        return ((PluginCompatibleStep) ciStepInfo).getResources();
      default:
        throw new CIStageExecutionException(
            format("%s step not allowed in builds", ciStepInfo.getNonYamlInfo().getStepInfoType()));
    }
  }

  public Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> getStepConnectorRefs(
      IntegrationStageConfig integrationStageConfig, Ambiance ambiance) {
    List<ExecutionWrapperConfig> executionWrappers = integrationStageConfig.getExecution().getSteps();
    if (isEmpty(executionWrappers)) {
      return Collections.emptyMap();
    }

    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> map = new HashMap<>();
    for (ExecutionWrapperConfig executionWrapperConfig : executionWrappers) {
      populateStepConnectorRefsUtil(executionWrapperConfig, ambiance, map);
    }
    return map;
  }

  public void populateStepConnectorRefsUtil(ExecutionWrapperConfig executionWrapperConfig, Ambiance ambiance,
      Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> map) {
    if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
      StepElementConfig stepElementConfig = getStepElementConfig(executionWrapperConfig);
      map.putAll(getStepConnectorConversionInfo(stepElementConfig, ambiance));
    } else if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElementConfig = getParallelStepElementConfig(executionWrapperConfig);
      for (ExecutionWrapperConfig executionWrapper : parallelStepElementConfig.getSections()) {
        populateStepConnectorRefsUtil(executionWrapper, ambiance, map);
      }
    } else if (executionWrapperConfig.getStepGroup() != null && !executionWrapperConfig.getStepGroup().isNull()) {
      StepGroupElementConfig stepGroupElementConfig =
          IntegrationStageUtils.getStepGroupElementConfig(executionWrapperConfig);
      for (ExecutionWrapperConfig executionWrapper : stepGroupElementConfig.getSteps()) {
        populateStepConnectorRefsUtil(executionWrapper, ambiance, map);
      }
    }
  }

  private Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> getStepConnectorConversionInfo(
      StepElementConfig stepElement, Ambiance ambiance) {
    Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> map = new HashMap<>();
    if ((stepElement.getStepSpecType() instanceof PluginCompatibleStep)
        && (stepElement.getStepSpecType() instanceof WithConnectorRef)) {
      map.put(stepElement.getIdentifier(), new ArrayList<>());
      PluginCompatibleStep step = (PluginCompatibleStep) stepElement.getStepSpecType();

      String connectorRef = PluginSettingUtils.getConnectorRef(step);
      Map<EnvVariableEnum, String> envToSecretMap =
          PluginSettingUtils.getConnectorSecretEnvMap(step.getNonYamlInfo().getStepInfoType());
      map.get(stepElement.getIdentifier())
          .add(K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                   .connectorRef(connectorRef)
                   .envToSecretsMap(envToSecretMap)
                   .build());
      List<K8BuildJobEnvInfo.ConnectorConversionInfo> baseConnectorConversionInfo =
          this.getBaseImageConnectorConversionInfo(step, ambiance);
      map.get(stepElement.getIdentifier()).addAll(baseConnectorConversionInfo);
    }
    return map;
  }

  private List<K8BuildJobEnvInfo.ConnectorConversionInfo> getBaseImageConnectorConversionInfo(
      PluginCompatibleStep step, Ambiance ambiance) {
    List<String> baseConnectorRefs = PluginSettingUtils.getBaseImageConnectorRefs(step);
    List<K8BuildJobEnvInfo.ConnectorConversionInfo> baseImageConnectorConversionInfos = new ArrayList<>();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (!isEmpty(baseConnectorRefs)) {
      baseImageConnectorConversionInfos =
          baseConnectorRefs.stream()
              .map(baseConnectorRef -> {
                CIStepInfoType stepInfoType;
                // get connector details
                ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, baseConnectorRef);
                switch (connectorDetails.getConnectorType()) {
                  case DOCKER:
                    stepInfoType = CIStepInfoType.DOCKER;
                    break;
                  default:
                    throw new IllegalStateException(
                        "Unexpected base connector: " + connectorDetails.getConnectorType());
                }
                Map<EnvVariableEnum, String> envToSecretMap = PluginSettingUtils.getConnectorSecretEnvMap(stepInfoType);
                return K8BuildJobEnvInfo.ConnectorConversionInfo.builder()
                    .connectorRef(baseConnectorRef)
                    .envToSecretsMap(envToSecretMap)
                    .build();
              })
              .collect(Collectors.toList());
    }
    return baseImageConnectorConversionInfos;
  }

  private ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  private Integer calculateExtraCPUForParallelStep(
      ParallelStepElementConfig parallelStepElementConfig, String accountId, Integer stageCpuRequest) {
    Integer executionWrapperCPURequest = 0;
    for (ExecutionWrapperConfig wrapper : parallelStepElementConfig.getSections()) {
      executionWrapperCPURequest += getExecutionWrapperCpuRequest(wrapper, accountId);
    }
    Integer extraCPUPerStep = 0;

    if (stageCpuRequest > executionWrapperCPURequest) {
      extraCPUPerStep = (stageCpuRequest - executionWrapperCPURequest) / parallelStepElementConfig.getSections().size();
    }

    return extraCPUPerStep;
  }

  private Integer calculateExtraMemoryForParallelStep(
      ParallelStepElementConfig parallelStepElementConfig, String accountId, Integer stageMemoryRequest) {
    Integer executionWrapperMemoryRequest = 0;
    for (ExecutionWrapperConfig wrapper : parallelStepElementConfig.getSections()) {
      executionWrapperMemoryRequest += getExecutionWrapperMemoryRequest(wrapper, accountId);
    }
    Integer extraMemoryPerStep = 0;

    if (stageMemoryRequest > executionWrapperMemoryRequest) {
      extraMemoryPerStep =
          (stageMemoryRequest - executionWrapperMemoryRequest) / parallelStepElementConfig.getSections().size();
    }

    return extraMemoryPerStep;
  }

  private Integer calculateExtraCPU(
      ExecutionWrapperConfig executionWrapper, String accountId, Integer maxAllocatableCpuRequest) {
    Integer executionWrapperCPURequest = getExecutionWrapperCpuRequest(executionWrapper, accountId);
    return Math.max(0, maxAllocatableCpuRequest - executionWrapperCPURequest);
  }

  private Integer calculateExtraMemory(
      ExecutionWrapperConfig executionWrapper, String accountId, Integer maxAllocatableMemoryRequest) {
    Integer executionWrapperMemoryRequest = getExecutionWrapperMemoryRequest(executionWrapper, accountId);
    return Math.max(0, maxAllocatableMemoryRequest - executionWrapperMemoryRequest);
  }

  private StepElementConfig getStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getStep().toString(), StepElementConfig.class);
    } catch (Exception ex) {
      throw new CIStageExecutionException("Failed to deserialize ExecutionWrapperConfig step node", ex);
    }
  }

  private ContainerResource getContainerResource(StepElementConfig stepElement) {
    if (!(stepElement.getStepSpecType() instanceof CIStepInfo)) {
      return null;
    }

    CIStepInfo ciStepInfo = (CIStepInfo) stepElement.getStepSpecType();
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return ((RunStepInfo) ciStepInfo).getResources();
      case DOCKER:
      case ECR:
      case GCR:
      case SAVE_CACHE_S3:
      case RESTORE_CACHE_S3:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_GCS:
      case SECURITY:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_S3:
      case UPLOAD_GCS:
      case GIT_CLONE:
        return ((PluginCompatibleStep) ciStepInfo).getResources();
      case PLUGIN:
        return ((PluginStepInfo) ciStepInfo).getResources();
      case RUN_TESTS:
        return ((RunTestsStepInfo) ciStepInfo).getResources();
      default:
        return null;
    }
  }

  //TODO: it may not make sense to overload this method for VM usage, but if it does this method needs put somewhere more generic
  //Overloaded for use with VMs - the base method is setup to handle nulls accountId and Os.
  public static PluginStepInfo createPluginStepInfo(GitCloneStepInfo gitCloneStepInfo,
                                                    CIExecutionConfigService ciExecutionConfigService) {
    return createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService, null, null);
  }

  /**
   * Create Plugin step info
   * Given a gitCloneStepInfo convert it into a PluginStepInfo (which is what codebase used originally for git clone)
   * @return PluginStepInfo with values set from GitCloneStepInfo
   */
  public static PluginStepInfo createPluginStepInfo(GitCloneStepInfo gitCloneStepInfo,
                                                    CIExecutionConfigService ciExecutionConfigService, String accountId,
                                                    OSType os) {
    Map<String, JsonNode> settings = new HashMap<>();

    Pair<String, String> buildEnvVar = getBuildEnvVar(gitCloneStepInfo);

    Integer depth = null;
    final ParameterField<Integer> depthParameter = gitCloneStepInfo.getDepth();
    if (depthParameter == null || depthParameter.getValue() == null) {
      if (buildEnvVar != null && isNotEmpty(buildEnvVar.getValue())) {
        depth = GIT_CLONE_MANUAL_DEPTH;
      }
    }

    if (depth != null && depth != 0) {
      settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, JsonNodeFactory.instance.textNode(depth.toString()));
    }

    Map<String, String> envVariables = new HashMap<>();
    if (gitCloneStepInfo.getSslVerify() != null && gitCloneStepInfo.getSslVerify().getValue() != null
            && !gitCloneStepInfo.getSslVerify().getValue()) {
      envVariables.put(GIT_SSL_NO_VERIFY, "true");
    }
    if (buildEnvVar != null) {
      String type = buildEnvVar.getKey();
      envVariables.put(type, buildEnvVar.getValue());
      if(DRONE_TAG.equals(type)) {
        envVariables.put(DRONE_BUILD_EVENT, "tag");
      }
    }

    CIExecutionServiceConfig ciExecutionServiceConfig = ciExecutionConfigService.getCiExecutionServiceConfig();
    List<String> entrypoint = Collections.emptyList();
    if (ciExecutionServiceConfig != null && ciExecutionServiceConfig.getStepConfig() != null
            && ciExecutionServiceConfig.getStepConfig().getGitCloneConfig() != null) {
      entrypoint = ciExecutionServiceConfig.getStepConfig().getGitCloneConfig().getEntrypoint();
      if (OSType.Windows == os) {
        entrypoint = ciExecutionServiceConfig.getStepConfig().getGitCloneConfig().getWindowsEntrypoint();
      }
    }

    PluginStepInfo step = PluginStepInfo.builder()
            .connectorRef(gitCloneStepInfo.getConnectorRef())
            .identifier(gitCloneStepInfo.getIdentifier())
            .name(gitCloneStepInfo.getName())
            .settings(ParameterField.createValueField(settings))
            .envVariables(envVariables)
            .entrypoint(entrypoint)
            .harnessManagedImage(true)
            .resources(gitCloneStepInfo.getResources())
            .privileged(ParameterField.createValueField(null))
            .reports(ParameterField.createValueField(null))
            .build();

    if (isNotEmpty(accountId)) {
      String gitCloneImage =
              ciExecutionConfigService.getPluginVersionForK8(CIStepInfoType.GIT_CLONE, accountId).getImage();
      step.setImage(ParameterField.createValueField(gitCloneImage));
    }
    return step;
  }

  /**
   * Get Build Env variable - branch or tag
   *
   * @param gitCloneStepInfo gitCloneStepInfo
   * @return a pair containing whether the build is configured for a branch or tag, and the value of the branch or tag
   */
  private static Pair<String, String> getBuildEnvVar(GitCloneStepInfo gitCloneStepInfo) {
    final String identifier = gitCloneStepInfo.getIdentifier();
    final String type = gitCloneStepInfo.getStepType().getType();
    Pair<String, String> buildEnvVar = null;
    Build build = RunTimeInputHandler.resolveBuild(gitCloneStepInfo.getBuild());
    if (build != null) {
      if (build.getType() == BuildType.PR) {
        throw new CIStageExecutionException(format("%s is not a valid build type in step type %s with identifier %s",
                BuildType.PR, type, identifier));
      } else if (build.getType() == BuildType.BRANCH) {
        ParameterField<String> branch = ((BranchBuildSpec) build.getSpec()).getBranch();
        String branchString =
                RunTimeInputHandler.resolveStringParameter("branch", type, identifier, branch, false);
        if (isNotEmpty(branchString)) {
          if (!branchString.equals(BRANCH_EXPRESSION)) {
            buildEnvVar = new ImmutablePair<>(DRONE_COMMIT_BRANCH, branchString);
          }
        } else {
          throw new CIStageExecutionException("Branch should not be empty for branch build type");
        }
      } else if (build.getType() == BuildType.TAG) {
        ParameterField<String> tag = ((TagBuildSpec) build.getSpec()).getTag();
        String tagString = RunTimeInputHandler.resolveStringParameter("tag", type, identifier, tag, false);
        if (isNotEmpty(tagString)) {
          buildEnvVar = new ImmutablePair<>(DRONE_TAG, tagString);
        } else {
          throw new CIStageExecutionException("Tag should not be empty for tag build type");
        }
      }
    }
    return buildEnvVar;
  }
}
