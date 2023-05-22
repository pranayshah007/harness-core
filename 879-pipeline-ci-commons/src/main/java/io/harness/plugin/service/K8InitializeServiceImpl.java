/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin.service;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.IMAGE_PATH_SPLIT_REGEX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.executionargs.ExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.cdng.plugininfoproviders.PluginExecutionConfig;
import io.harness.cdng.plugininfoproviders.PluginInfoProviderHelper;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class K8InitializeServiceImpl implements K8sInitializeService {
  public static final Integer STEP_REQUEST_MILLI_CPU = 10;
  public static final Integer STEP_REQUEST_MEMORY_MIB = 10;

  @Inject PluginService pluginService;
  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Override
  public ContainerDefinitionInfo createPluginCompatibleStepContainerDefinition(PluginCompatibleStep stepInfo,
      AbstractStageNode stageNode, ExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String stepName, String stepType, long timeout, String accountId, OSType os, Ambiance ambiance,
      Integer extraMemoryPerStep, Integer extraCPUPerStep) {
    Integer port = portFinder.getNextPort();

    String containerName = format("%s%d", STEP_PREFIX, stepIndex);
    Map<String, String> envVarMap = getEnvironmentVarMap(stageNode);
    envVarMap.putAll(pluginService.getPluginCompatibleEnvVariables(
        stepInfo, identifier, timeout, ambiance, StageInfraDetails.Type.K8, false, true));
    Integer runAsUser = resolveIntegerParameter(stepInfo.getRunAsUser(), null);

    Map<String, SecretNGVariable> secretVarMap = getSecretMap(stageNode);
    secretVarMap.putAll(pluginService.getPluginCompatibleSecretVars(stepInfo));

    Boolean privileged = null;

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(envVarMap)
        .secretVariables(new ArrayList<>(secretVarMap.values()))
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(getImageInfo(getImage(stepInfo, StageInfraDetails.Type.K8, accountId)))
                                   .build())
        .isHarnessManagedImage(true)
        .containerResourceParams(getContainerResources(
            stepInfo.getResources(), stepType, identifier, accountId, extraMemoryPerStep, extraCPUPerStep))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.PLUGIN)
        .stepIdentifier(identifier)
        .stepName(stepName)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(new ParameterField<>()))
        .privileged(privileged)
        .runAsUser(runAsUser)
        .build();
  }

  @Override
  public ContainerResourceParams getContainerResources(ContainerResource resources, String stepType, String identifier,
      String accountId, Integer extraMemoryPerStep, Integer extraCPUPerStep) {
    return ContainerResourceParams.builder()
        .resourceLimitMemoryMiB(PluginInfoProviderHelper.getCPU(resources))
        .resourceLimitMilliCpu(PluginInfoProviderHelper.getCPU(resources))
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .build();
  }

  @Override
  public Map<String, String> getEnvironmentVarMap(AbstractStageNode stageNode) {
    return new HashMap<>();
  }

  @Override
  public Map<String, SecretNGVariable> getSecretMap(AbstractStageNode stageNode) {
    return new HashMap<>();
  }

  @Override
  public String getImage(PluginCompatibleStep step, StageInfraDetails.Type infraType, String accountId) {
    return pluginExecutionConfig.getGitCloneConfig().getImage();
  }

  public static ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (isNotEmpty(image)) {
      if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
        String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
        if (subTokens.length > 1) {
          tag = subTokens[subTokens.length - 1];
          String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
          name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
        }
      }
    } else {
      throw new CIStageExecutionException(format("ConnectorRef and Image should not be empty"));
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }
}
