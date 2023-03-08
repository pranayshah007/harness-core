/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_SERVICE_ARGS;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_SERVICE_ENTRYPOINT;
import static io.harness.ci.commonconstants.CIExecutionConstants.SERVICE_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.quantity.unit.DecimalQuantityUnit;
import io.harness.beans.quantity.unit.StorageQuantityUnit;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.ServiceContainerUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.utils.PortFinder;
import io.harness.ci.utils.QuantityUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class K8InitializeServiceUtils {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  private static final String SEPARATOR = ",";

  public List<ContainerDefinitionInfo> createServiceContainerDefinitions(
      IntegrationStageNode stageNode, PortFinder portFinder, OSType os) {
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    IntegrationStageConfig integrationStage = IntegrationStageUtils.getIntegrationStageConfig(stageNode);
    if (integrationStage.getServiceDependencies() == null
        || isEmpty(integrationStage.getServiceDependencies().getValue())) {
      return containerDefinitionInfos;
    }

    int serviceIdx = 0;
    for (DependencyElement dependencyElement : integrationStage.getServiceDependencies().getValue()) {
      if (dependencyElement == null) {
        continue;
      }

      if (dependencyElement.getDependencySpecType() instanceof CIServiceInfo) {
        ContainerDefinitionInfo containerDefinitionInfo =
            createServiceContainerDefinition((CIServiceInfo) dependencyElement.getDependencySpecType(), portFinder,
                serviceIdx, stageNode.getIdentifier(), os);
        if (containerDefinitionInfo != null) {
          containerDefinitionInfos.add(containerDefinitionInfo);
        }
        serviceIdx++;
      }
    }
    return containerDefinitionInfos;
  }

  private ContainerDefinitionInfo createServiceContainerDefinition(
      CIServiceInfo service, PortFinder portFinder, int serviceIdx, String identifier, OSType os) {
    Integer port = portFinder.getNextPort();
    service.setGrpcPort(port);

    String containerName = String.format("%s%d", SERVICE_PREFIX, serviceIdx);
    String image =
        RunTimeInputHandler.resolveStringParameter("image", "serviceDependency", identifier, service.getImage(), true);

    String connectorRef = RunTimeInputHandler.resolveStringParameter(
        "connectorRef", "serviceDependency", identifier, service.getConnectorRef(), true);

    Boolean privileged = service.getPrivileged().getValue();
    Integer runAsUser = resolveIntegerParameter(service.getRunAsUser(), null);

    List<String> args =
        RunTimeInputHandler.resolveListParameter("args", "serviceDependency", identifier, service.getArgs(), false);

    List<String> entrypoint = RunTimeInputHandler.resolveListParameter(
        "entrypoint", "serviceDependency", identifier, service.getEntrypoint(), false);

    Map<String, String> envVariables = RunTimeInputHandler.resolveMapParameter(
        "envVariables", "serviceDependency", identifier, service.getEnvVariables(), false);

    Map<String, String> portBindings = RunTimeInputHandler.resolveMapParameter(
        "portBindings", "serviceDependency", identifier, service.getPortBindings(), false);
    if (isNotEmpty(portBindings)) {
      throw new CIStageExecutionException("port bindings can't be set in k8s infrastructure");
    }

    if (envVariables == null) {
      envVariables = new HashMap<>();
    }
    if (isNotEmpty(entrypoint)) {
      envVariables.put(HARNESS_SERVICE_ENTRYPOINT, String.join(SEPARATOR, entrypoint));
    }
    if (isNotEmpty(args)) {
      envVariables.put(HARNESS_SERVICE_ARGS, String.join(SEPARATOR, args));
    }

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(ServiceContainerUtils.getCommand(os))
        .args(ServiceContainerUtils.getArguments(service.getIdentifier(), image, port))
        .envVars(envVariables)
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(IntegrationStageUtils.getImageInfo(image))
                                   .connectorIdentifier(connectorRef)
                                   .build())
        .containerResourceParams(getServiceContainerResource(service.getResources(), service.getIdentifier()))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.SERVICE)
        .stepIdentifier(service.getIdentifier())
        .stepName(service.getName())
        .privileged(privileged)
        .runAsUser(runAsUser)
        .imagePullPolicy(RunTimeInputHandler.resolveImagePullPolicy(service.getImagePullPolicy()))
        .build();
  }

  private ContainerResourceParams getServiceContainerResource(ContainerResource resource, String identifier) {
    CIExecutionServiceConfig ciExecutionServiceConfig = ciExecutionConfigService.getCiExecutionServiceConfig();
    Integer cpu = ciExecutionServiceConfig.getDefaultCPULimit();
    Integer memory = ciExecutionServiceConfig.getDefaultMemoryLimit();

    if (resource != null && resource.getLimits() != null) {
      if (resource.getLimits().getCpu() != null) {
        String cpuQuantity = resolveStringParameter("cpu", "Service", identifier, resource.getLimits().getCpu(), false);
        if (isNotEmpty(cpuQuantity) && !UNRESOLVED_PARAMETER.equals(cpuQuantity)) {
          cpu = QuantityUtils.getCpuQuantityValueInUnit(cpuQuantity, DecimalQuantityUnit.m);
        }
      }
      if (resource.getLimits().getMemory() != null) {
        String memoryQuantity = RunTimeInputHandler.resolveStringParameter(
            "memory", "Service", identifier, resource.getLimits().getMemory(), false);
        if (isNotEmpty(memoryQuantity) && !UNRESOLVED_PARAMETER.equals(memoryQuantity)) {
          memory = QuantityUtils.getStorageQuantityValueInUnit(memoryQuantity, StorageQuantityUnit.Mi);
        }
      }
    }
    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(cpu)
        .resourceRequestMemoryMiB(memory)
        .resourceLimitMilliCpu(cpu)
        .resourceLimitMemoryMiB(memory)
        .build();
  }

  public List<ServiceDefinitionInfo> getServiceInfos(IntegrationStageConfig integrationStage) {
    if (integrationStage.getServiceDependencies() == null
        || isEmpty(integrationStage.getServiceDependencies().getValue())) {
      return new ArrayList<>();
    }

    int serviceIdx = 0;
    List<ServiceDefinitionInfo> serviceDefinitionInfos = new ArrayList<>();
    for (DependencyElement dependencyElement : integrationStage.getServiceDependencies().getValue()) {
      if (dependencyElement == null) {
        continue;
      }

      if (dependencyElement.getDependencySpecType() instanceof CIServiceInfo) {
        String containerName = String.format("%s%d", SERVICE_PREFIX, serviceIdx);
        CIServiceInfo ciServiceInfo = (CIServiceInfo) dependencyElement.getDependencySpecType();
        String image = RunTimeInputHandler.resolveStringParameter(
            "image", "serviceDependency", ciServiceInfo.getIdentifier(), ciServiceInfo.getImage(), true);
        serviceDefinitionInfos.add(ServiceDefinitionInfo.builder()
                                       .identifier(ciServiceInfo.getIdentifier())
                                       .name(ciServiceInfo.getName())
                                       .image(image)
                                       .containerName(containerName)
                                       .build());
        serviceIdx++;
      }
    }
    return serviceDefinitionInfos;
  }
}
