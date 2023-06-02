/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.ci.commonconstants.CIExecutionConstants.ID_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.IMAGE_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.PORT_PREFIX;
import static io.harness.ci.commonconstants.CIExecutionConstants.SERVICE_ARG_COMMAND;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.delegate.beans.ci.pod.CIContainerType.SERVICE;
import static io.harness.pms.yaml.ParameterField.createValueField;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.k8s.model.ImageDetails;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Singleton
@OwnedBy(CI)
public class K8InitializeServiceUtilsHelper {
  private static final String SERVICE_ID = "db";
  private static final String SERVICE_NAME = "db";
  private static final String SERVICE_CTR_NAME = "service-0";
  private static final String SERVICE_LIMIT_MEM_STRING = "60Mi";
  private static final String SERVICE_LIMIT_CPU_STRING = "80m";
  private static final Integer SERVICE_LIMIT_MEM = 60;
  private static final Integer SERVICE_LIMIT_CPU = 80;
  private static final String SERVICE_IMAGE = "redis";
  private static final String SERVICE_ENTRYPOINT = "redis";
  private static final String SERVICE_ARGS = "start";
  private static final String SERVICE_CONNECTOR_REF = "dockerConnector";

  public static final Integer PORT_STARTING_RANGE = 20002;

  private static DependencyElement getServiceDependencyElement() {
    return DependencyElement.builder()
        .identifier(SERVICE_ID)
        .dependencySpecType(CIServiceInfo.builder()
                                .identifier(SERVICE_ID)
                                .name(SERVICE_NAME)
                                .args(createValueField(Collections.singletonList(SERVICE_ARGS)))
                                .entrypoint(createValueField(Collections.singletonList(SERVICE_ENTRYPOINT)))
                                .image(createValueField(SERVICE_IMAGE))
                                .connectorRef(createValueField(SERVICE_CONNECTOR_REF))
                                .privileged(createValueField(null))
                                .imagePullPolicy(createValueField(null))
                                .resources(ContainerResource.builder()
                                               .limits(ContainerResource.Limits.builder()
                                                           .cpu(createValueField(SERVICE_LIMIT_CPU_STRING))
                                                           .memory(createValueField(SERVICE_LIMIT_MEM_STRING))
                                                           .build())
                                               .build())
                                .build())
        .build();
  }

  public static ServiceDefinitionInfo getServiceDefintion() {
    return ServiceDefinitionInfo.builder()
        .image(SERVICE_IMAGE)
        .name(SERVICE_NAME)
        .containerName(SERVICE_CTR_NAME)
        .identifier(SERVICE_ID)
        .build();
  }

  public static ContainerDefinitionInfo getServiceContainer() {
    Integer port = PORT_STARTING_RANGE;
    List<String> args = Arrays.asList(
        SERVICE_ARG_COMMAND, ID_PREFIX, SERVICE_ID, IMAGE_PREFIX, SERVICE_IMAGE, PORT_PREFIX, port.toString());

    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(SERVICE_IMAGE).tag("").build())
                                   .connectorIdentifier("dockerConnector")
                                   .build())
        .name(SERVICE_CTR_NAME)
        .stepName(SERVICE_NAME)
        .envVars(ImmutableMap.of("HARNESS_SERVICE_ENTRYPOINT", "redis", "HARNESS_SERVICE_ARGS", "start"))
        .containerType(SERVICE)
        .args(args)
        .commands(asList(UNIX_STEP_COMMAND))
        .ports(asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(SERVICE_LIMIT_CPU)
                                     .resourceRequestMemoryMiB(SERVICE_LIMIT_MEM)
                                     .resourceLimitMilliCpu(SERVICE_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(SERVICE_LIMIT_MEM)
                                     .build())
        .stepIdentifier(SERVICE_ID)
        .build();
  }

  public static IntegrationStageNode getStageNode() {
    return IntegrationStageNode.builder()
        .identifier("ciStage")
        .type(IntegrationStageNode.StepType.CI)
        .integrationStageConfig((IntegrationStageConfigImpl) getIntegrationStageConfig())
        .build();
  }

  public static IntegrationStageConfig getIntegrationStageConfig() {
    return IntegrationStageConfigImpl.builder()
        .serviceDependencies(ParameterField.createValueField(Collections.singletonList(getServiceDependencyElement())))
        .build();
  }
}
