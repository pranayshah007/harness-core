/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plugin;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.ModuleType;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.pluginstep.ContainerStepV2PluginProvider;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ConnectorDetails;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationBatchRequest;
import io.harness.pms.contracts.plan.PluginCreationBatchResponse;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseV2;
import io.harness.pms.contracts.plan.PluginInfoProviderServiceGrpc;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerExecutionConfig;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
public class ContainerStepV2PluginProviderImpl implements ContainerStepV2PluginProvider {
  @Inject PmsSdkInstanceService pmsSdkInstanceService;
  @Inject
  Map<ModuleType, PluginInfoProviderServiceGrpc.PluginInfoProviderServiceBlockingStub>
      pluginInfoProviderServiceBlockingStubMap;
  @Inject K8sPodInitUtils k8sPodInitUtils;
  @Inject ContainerExecutionConfig containerExecutionConfig;

  @Override
  public Map<StepInfo, PluginCreationResponse> getPluginsData(
      InitContainerV2StepInfo initContainerV2StepInfo, Ambiance ambiance) {
    Set<StepInfo> stepInfos = getStepInfos(initContainerV2StepInfo.getStepsExecutionConfig());
    Set<Integer> usedPorts = new HashSet<>();
    return stepInfos.stream()
        .map(stepInfo -> {
          OSType os = k8sPodInitUtils.getOS(initContainerV2StepInfo.getInfrastructure());
          PluginCreationResponse pluginInfo =
              pluginInfoProviderServiceBlockingStubMap.get(ModuleType.fromString(stepInfo.getModuleType()))
                  .getPluginInfos(PluginCreationRequest.newBuilder()
                                      .setType(stepInfo.getStepType())
                                      .setStepJsonNode(stepInfo.getExecutionWrapperConfig().getStep().toString())
                                      .setAmbiance(ambiance)
                                      .setAccountId(AmbianceUtils.getAccountId(ambiance))
                                      .setOsType(os.getYamlName())
                                      .setUsedPortDetails(PortDetails.newBuilder().addAllUsedPorts(usedPorts).build())
                                      .build());
          if (pluginInfo.hasError()) {
            log.error("Encountered error in plugin info collection {}", pluginInfo.getError());
            throw new ContainerStepExecutionException(pluginInfo.getError().getMessagesList().toString());
          }
          if (isEmpty(pluginInfo.getPluginDetails().getImageDetails().getConnectorDetails().getConnectorRef())) {
            ConnectorDetails connectorDetails = pluginInfo.getPluginDetails()
                                                    .getImageDetails()
                                                    .getConnectorDetails()
                                                    .toBuilder()
                                                    .setConnectorRef(getConnectorRef(initContainerV2StepInfo, stepInfo))
                                                    .build();
            ImageDetails imageDetails = pluginInfo.toBuilder()
                                            .getPluginDetails()
                                            .getImageDetails()
                                            .toBuilder()
                                            .setConnectorDetails(connectorDetails)
                                            .build();
            pluginInfo =
                pluginInfo.toBuilder()
                    .setPluginDetails(pluginInfo.getPluginDetails().toBuilder().setImageDetails(imageDetails).build())
                    .build();
          }
          usedPorts.addAll(pluginInfo.getPluginDetails().getTotalPortUsedDetails().getUsedPortsList());
          return Pair.of(stepInfo, pluginInfo);
        })
        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
  }

  @Override
  public Map<StepInfo, PluginCreationResponseList> getPluginsDataV2(
      InitContainerV2StepInfo initContainerV2StepInfo, Ambiance ambiance) {
    Set<StepInfo> stepInfos = getStepInfos(initContainerV2StepInfo.getStepsExecutionConfig());
    Set<Integer> usedPorts = new HashSet<>();
    OSType os = k8sPodInitUtils.getOS(initContainerV2StepInfo.getInfrastructure());
    Map<String, Map<StepInfo, PluginCreationRequest>> moduleToBatchRequest =
        getModuleToBatchRequest(stepInfos, ambiance, usedPorts, os);
    Map<StepInfo, PluginCreationResponseList> stepInfoPluginCreationResponseListMap = new HashMap<>();
    for (Map.Entry<String, Map<StepInfo, PluginCreationRequest>> entry : moduleToBatchRequest.entrySet()) {
      PluginCreationBatchRequest pluginCreationBatchRequest =
          PluginCreationBatchRequest.newBuilder().addAllPluginCreationRequest(entry.getValue().values()).build();
      Map<String, StepInfo> stepInfoUuidToStepInfo =
          entry.getValue().keySet().stream().collect(Collectors.toMap(StepInfo::getStepUuid, stepInfo -> stepInfo));
      PluginCreationBatchResponse pluginCreationBatchResponse =
          pluginInfoProviderServiceBlockingStubMap.get(ModuleType.fromString(entry.getKey()))
              .getPluginInfosList(pluginCreationBatchRequest);
      for (Map.Entry<String, PluginCreationResponseList> response :
          pluginCreationBatchResponse.getRequestIdToResponseMap().entrySet()) {
        PluginCreationResponseList responseList2 = response.getValue();
        PluginCreationResponseList.Builder responseList = PluginCreationResponseList.newBuilder();
        for (PluginCreationResponseV2 responseV2 : responseList2.getResponseList()) {
          PluginCreationResponse pluginInfo = responseV2.getResponse();

          if (pluginInfo.hasError()) {
            log.error("Encountered error in plugin info collection {}", pluginInfo.getError());
            throw new ContainerStepExecutionException(pluginInfo.getError().getMessagesList().toString());
          }
          if (isEmpty(pluginInfo.getPluginDetails().getImageDetails().getConnectorDetails().getConnectorRef())) {
            ConnectorDetails connectorDetails =
                pluginInfo.getPluginDetails()
                    .getImageDetails()
                    .getConnectorDetails()
                    .toBuilder()
                    .setConnectorRef(getConnectorRef(initContainerV2StepInfo, responseV2.getStepInfo().getIdentifier()))
                    .build();
            ImageDetails imageDetails = pluginInfo.toBuilder()
                                            .getPluginDetails()
                                            .getImageDetails()
                                            .toBuilder()
                                            .setConnectorDetails(connectorDetails)
                                            .build();
            pluginInfo =
                pluginInfo.toBuilder()
                    .setPluginDetails(pluginInfo.getPluginDetails().toBuilder().setImageDetails(imageDetails).build())
                    .build();
          }
          responseList.addResponse(PluginCreationResponseV2.newBuilder()
                                       .setStepInfo(responseV2.getStepInfo())
                                       .setResponse(pluginInfo)
                                       .build());
          usedPorts.addAll(pluginInfo.getPluginDetails().getTotalPortUsedDetails().getUsedPortsList());
        }
        stepInfoPluginCreationResponseListMap.put(stepInfoUuidToStepInfo.get(response.getKey()), responseList.build());
      }
    }
    return stepInfoPluginCreationResponseListMap;
  }

  private Map<String, Map<StepInfo, PluginCreationRequest>> getModuleToBatchRequest(
      Set<StepInfo> stepInfos, Ambiance ambiance, Set<Integer> usedPorts, OSType os) {
    Multimap<String, StepInfo> moduleToStepInfo = HashMultimap.create();
    for (StepInfo stepInfo : stepInfos) {
      moduleToStepInfo.put(stepInfo.getModuleType(), stepInfo);
    }
    Map<String, Map<StepInfo, PluginCreationRequest>> map = new HashMap<>();
    for (Map.Entry<String, Collection<StepInfo>> entry : moduleToStepInfo.asMap().entrySet()) {
      for (StepInfo stepInfo : entry.getValue()) {
        map.put(entry.getKey(),
            Map.of(stepInfo,
                PluginCreationRequest.newBuilder()
                    .setType(stepInfo.getStepType())
                    .setStepJsonNode(stepInfo.getExecutionWrapperConfig().getStep().toString())
                    .setAmbiance(ambiance)
                    .setAccountId(AmbianceUtils.getAccountId(ambiance))
                    .setOsType(os.getYamlName())
                    .setUsedPortDetails(PortDetails.newBuilder().addAllUsedPorts(usedPorts).build())
                    .setRequestId(stepInfo.getStepUuid())
                    .build()));
      }
    }
    return map;
  }

  private String getConnectorRef(InitContainerV2StepInfo initContainerV2StepInfo, StepInfo stepInfo) {
    if (initContainerV2StepInfo.getInfrastructure() instanceof ContainerK8sInfra) {
      ParameterField<String> harnessImageConnectorRef =
          ((ContainerK8sInfra) initContainerV2StepInfo.getInfrastructure()).getSpec().getHarnessImageConnectorRef();
      if (harnessImageConnectorRef != null) {
        return harnessImageConnectorRef.getValue();
      }
    }
    log.info("Defaulting to default connector for step {}", stepInfo.getStepIdentifier());
    return containerExecutionConfig.getDefaultInternalImageConnector();
  }

  private String getConnectorRef(InitContainerV2StepInfo initContainerV2StepInfo, String stepIdentifier) {
    if (initContainerV2StepInfo.getInfrastructure() instanceof ContainerK8sInfra) {
      ParameterField<String> harnessImageConnectorRef =
          ((ContainerK8sInfra) initContainerV2StepInfo.getInfrastructure()).getSpec().getHarnessImageConnectorRef();
      if (harnessImageConnectorRef != null) {
        return harnessImageConnectorRef.getValue();
      }
    }
    log.info("Defaulting to default connector for step {}", stepIdentifier);
    return containerExecutionConfig.getDefaultInternalImageConnector();
  }

  private ParallelStepElementConfig getParallelStepElementConfig(ExecutionWrapperConfig executionWrapperConfig) {
    try {
      return YamlUtils.read(executionWrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
    } catch (Exception ex) {
      throw new ContainerStepExecutionException("Failed to deserialize ExecutionWrapperConfig parallel node", ex);
    }
  }

  private Set<StepInfo> getStepInfos(StepsExecutionConfig stepExecutionConfig) {
    List<ExecutionWrapperConfig> executionWrappers = stepExecutionConfig.getSteps();
    Set<StepInfo> stepInfos = new HashSet<>();
    for (ExecutionWrapperConfig executionWrapper : executionWrappers) {
      getStepType(executionWrapper, stepInfos);
    }
    return stepInfos;
  }

  private void getStepType(ExecutionWrapperConfig executionWrapperConfig, Set<StepInfo> stepInfos) {
    if (executionWrapperConfig.getStep() != null && !executionWrapperConfig.getStep().isNull()) {
      YamlNode yamlNode = new YamlNode(executionWrapperConfig.getStep());
      Optional<String> moduleForStep = getModuleForStep(yamlNode.getType());
      if (!moduleForStep.isPresent()) {
        throw new ContainerStepExecutionException(String.format("No module found for step %s", yamlNode.getType()));
      }
      stepInfos.add(StepInfo.builder()
                        .stepType(yamlNode.getType())
                        .moduleType(moduleForStep.get())
                        .executionWrapperConfig(executionWrapperConfig)
                        .stepUuid(yamlNode.getUuid())
                        .stepIdentifier(yamlNode.getIdentifier())
                        .build());

    } else if (executionWrapperConfig.getParallel() != null && !executionWrapperConfig.getParallel().isNull()) {
      ParallelStepElementConfig parallelStepElement = getParallelStepElementConfig(executionWrapperConfig);
      if (isNotEmpty(parallelStepElement.getSections())) {
        for (ExecutionWrapperConfig wrapper : parallelStepElement.getSections()) {
          getStepType(wrapper, stepInfos);
        }
      }
    }
  }

  private Optional<String> getModuleForStep(String type) {
    if (isEmpty(type)) {
      return Optional.empty();
    }
    Map<String, Set<SdkStep>> sdkSteps = pmsSdkInstanceService.getSdkSteps();
    return sdkSteps.entrySet()
        .stream()
        .map(moduleSdkStepMap -> {
          Optional<SdkStep> step = moduleSdkStepMap.getValue()
                                       .stream()
                                       .filter(sdkStep -> sdkStep.getStepType().getType().equals(type))
                                       .findFirst();
          if (step.isPresent()) {
            return moduleSdkStepMap.getKey();
          }
          return null;
        })
        .filter(Objects::nonNull)
        .findFirst();
  }
}
