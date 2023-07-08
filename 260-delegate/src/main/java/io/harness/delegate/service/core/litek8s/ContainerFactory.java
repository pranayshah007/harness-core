/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import io.harness.delegate.core.beans.*;
import io.harness.delegate.service.core.k8s.K8SEnvVar;
import io.harness.delegate.service.core.util.K8SResourceHelper;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1CapabilitiesBuilder;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1ContainerPortBuilder;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1ResourceRequirementsBuilder;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1SecurityContextBuilder;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ContainerFactory {
  private static final String PLUGIN_DOCKER_IMAGE_NAME = "plugins/docker";
  private static final String PLUGIN_ECR_IMAGE_NAME = "plugins/ecr";
  private static final String PLUGIN_ACR_IMAGE_NAME = "plugins/acr";
  private static final String PLUGIN_GCR_IMAGE_NAME = "plugins/gcr";
  private static final String PLUGIN_HEROKU_IMAGE_NAME = "plugins/heroku";

  private static final String DOCKER_IMAGE_NAME = "docker:";
  private static final String DIND_TAG = "dind";

  private static final String SETUP_ADDON_CONTAINER_NAME = "setup-addon";
  private static final String LE_CONTAINER_NAME = "lite-engine";
  private static final String HARNESS_WORKSPACE = "HARNESS_WORKSPACE";
  private static final String HARNESS_CI_INDIRECT_LOG_UPLOAD_FF = "HARNESS_CI_INDIRECT_LOG_UPLOAD_FF";
  private static final String HARNESS_LE_STATUS_REST_ENABLED = "HARNESS_LE_STATUS_REST_ENABLED";
  private static final String DELEGATE_SERVICE_ENDPOINT_VARIABLE = "DELEGATE_SERVICE_ENDPOINT";
  private static final String DELEGATE_SERVICE_ID_VARIABLE = "DELEGATE_SERVICE_ID";
  private static final String HARNESS_ACCOUNT_ID_VARIABLE = "HARNESS_ACCOUNT_ID";
  private static final String HARNESS_LOG_PREFIX = "HARNESS_LOG_PREFIX";
  private static final String HARNESS_LOG_SERVICE_ENDPOINT = "HARNESS_LOG_SERVICE_ENDPOINT";
  private static final String HARNESS_LOG_SERVICE_TOKEN = "HARNESS_LOG_SERVICE_TOKEN";
  private static final String TASK_PARAMETERS_FILE = "TASK_PARAMETERS_FILE";
  private static final String TASK_DATA_PATH = "TASK_DATA_PATH";
  private static final String DELEGATE_TOKEN = "DELEGATE_TOKEN";
  private static final String TASK_ID = "TASK_ID";

  private static final String WORKING_DIR = "/harness";
  private static final String ADDON_RUN_COMMAND = "/addon/bin/ci-addon";
  private static final String ADDON_RUN_ARGS_FORMAT = "--port";
  public static final int RESERVED_LE_PORT = 20001;
  public static final int RESERVED_ADDON_PORT = 20002;

  private final K8SRunnerConfig config;

  public V1ContainerBuilder createContainer(final K8SInfra k8SInfra, final K8SStep step, final int port) {
    String taskId = step.getId();
    StepRuntime containerRuntime = step.getRuntime();
    final var envVars = ImmutableMap.<String, String>builder();
    envVars.putAll(containerRuntime.getEnvMap());
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, config.getAccountId());
    envVars.put(HARNESS_LOG_PREFIX, k8SInfra.getLogPrefix());
    envVars.put(HARNESS_LOG_SERVICE_ENDPOINT, config.getLogServiceUrl());
    envVars.put(HARNESS_LOG_SERVICE_TOKEN, k8SInfra.getLogToken());
    envVars.put(DELEGATE_TOKEN, config.getDelegateToken());
    envVars.put(TASK_PARAMETERS_FILE, config.getDelegateTaskParamsFile());
    envVars.put(TASK_DATA_PATH, config.getDelegateTaskParamsFile());
    envVars.put(TASK_ID, taskId);
    final V1ContainerBuilder containerBuilder = new V1ContainerBuilder()
                                                    .withName(K8SResourceHelper.getContainerName(taskId))
                                                    .withImage(containerRuntime.getImage())
                                                    .withCommand(ADDON_RUN_COMMAND)
                                                    .withArgs(List.of(ADDON_RUN_ARGS_FORMAT, String.valueOf(port)))
                                                    .withPorts(getPort(port))
                                                    .withEnv(K8SEnvVar.fromMap(envVars.build()))
                                                    .withResources(getResources(containerRuntime.getResource().getCpu(),
                                                        containerRuntime.getResource().getMemory()))
                                                    .withImagePullPolicy("Always");

    if (containerRuntime.hasResource()) {
      containerBuilder.withResources(
          getResources(containerRuntime.getResource().getCpu(), containerRuntime.getResource().getMemory()));
    }

    if (containerRuntime.hasSecurityContext()) {
      final boolean isPrivilegedImage = isPrivilegedImage(containerRuntime.getImage());
      containerBuilder.withSecurityContext(
          getSecurityContext(containerRuntime.getSecurityContext(), isPrivilegedImage));
    }

    if (Strings.isNullOrEmpty(containerRuntime.getWorkingDir())) {
      containerBuilder.withWorkingDir(containerRuntime.getWorkingDir());
    }

    return containerBuilder;
  }

  public V1ContainerBuilder createAddonInitContainer(K8SInfra k8SInfra) {
    ContainerSpec containerSpec = k8SInfra.getAddonContainer();
    final var envVars = ImmutableMap.<String, String>builder();
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, config.getAccountId());
    envVars.put(HARNESS_LOG_PREFIX, k8SInfra.getLogPrefix());
    envVars.put(HARNESS_LOG_SERVICE_ENDPOINT, config.getLogServiceUrl());
    envVars.put(HARNESS_LOG_SERVICE_TOKEN, k8SInfra.getLogToken());
    return new V1ContainerBuilder()
        .withName(SETUP_ADDON_CONTAINER_NAME)
        .withImage(containerSpec.getImage())
        .withEnv(K8SEnvVar.fromMap(envVars.build()))
        .withCommand(containerSpec.getCommandList())
        .withArgs(containerSpec.getArgsList())
        .withImagePullPolicy("Always")
        .withResources(getResources("100m", "100Mi"))
        .withWorkingDir(containerSpec.getWorkingDir());
  }

  public V1ContainerBuilder createLEContainer(final K8SInfra k8SInfra) {
    return new V1ContainerBuilder()
        .withName(LE_CONTAINER_NAME)
        .withImage(k8SInfra.getLeContainer().getImage())
        .withEnv(K8SEnvVar.fromMap(getLeEnvVars()))
        .withImagePullPolicy("Always")
        .withPorts(getPort(k8SInfra.getLeContainer().getPort()))
        .withResources(getResources(k8SInfra.getResource().getCpu(), k8SInfra.getResource().getMemory()))
        .withWorkingDir(k8SInfra.getLeContainer().getWorkingDir());
  }

  private Map<String, String> getLeEnvVars() {
    final var envVars = ImmutableMap.<String, String>builder();
    envVars.put(HARNESS_WORKSPACE, ContainerFactory.WORKING_DIR);
    envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    envVars.put(HARNESS_LE_STATUS_REST_ENABLED, "true");
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE,
        "delegate-service"); // Fixme: LE Can't start without it. Should use service discovery instead
    envVars.put(DELEGATE_SERVICE_ID_VARIABLE, "delegate-grpc-service"); // fixme: What's this for?
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, config.getAccountId());
    //    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    //    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    //    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    //    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    //    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    //    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    return envVars.build();
  }

  @NonNull
  private V1ContainerPort getPort(final int port) {
    return new V1ContainerPortBuilder().withContainerPort(port).build();
  }

  private V1SecurityContext getSecurityContext(final SecurityContext securityContext, final boolean isPrivilegedImage) {
    final V1SecurityContextBuilder builder =
        new V1SecurityContextBuilder()
            .withAllowPrivilegeEscalation(securityContext.getAllowPrivilegeEscalation())
            .withPrivileged(isPrivilegedImage || securityContext.getPrivileged())
            .withReadOnlyRootFilesystem(securityContext.getReadOnlyRootFilesystem())
            .withRunAsNonRoot(securityContext.getRunAsNonRoot())
            .withCapabilities(new V1CapabilitiesBuilder()
                                  .withAdd(securityContext.getAddCapabilityList())
                                  .withDrop(securityContext.getDropCapabilityList())
                                  .build());

    if (securityContext.getRunAsUser() != 0) {
      builder.withRunAsUser(securityContext.getRunAsUser());
    }
    if (securityContext.getRunAsGroup() != 0) {
      builder.withRunAsGroup(securityContext.getRunAsGroup());
    }
    if (Strings.isNullOrEmpty(securityContext.getProcMount())) {
      builder.withProcMount(securityContext.getProcMount());
    }
    return builder.build();
  }

  private boolean isPrivilegedImage(final String image) {
    if (image.startsWith(PLUGIN_DOCKER_IMAGE_NAME) || image.startsWith(PLUGIN_ECR_IMAGE_NAME)
        || image.startsWith(PLUGIN_ACR_IMAGE_NAME) || image.startsWith(PLUGIN_GCR_IMAGE_NAME)
        || image.startsWith(PLUGIN_HEROKU_IMAGE_NAME)) {
      return true;
    }
    return image.startsWith(DOCKER_IMAGE_NAME) && image.contains(DIND_TAG);
  }

  private V1ResourceRequirements getResources(final String cpu, final String memory) {
    final var limitBuilder = ImmutableMap.<String, Quantity>builder();
    final var requestBuilder = ImmutableMap.<String, Quantity>builder();

    if (!Strings.isNullOrEmpty(cpu)) {
      requestBuilder.put("cpu", Quantity.fromString(cpu));
    }

    if (!Strings.isNullOrEmpty(memory)) {
      requestBuilder.put("memory", Quantity.fromString(memory));
      limitBuilder.put("memory", Quantity.fromString(memory));
    }

    final var requests = requestBuilder.build();
    final var limits = limitBuilder.build();
    final var resources = new V1ResourceRequirementsBuilder();
    if (!requests.isEmpty()) {
      resources.withRequests(requests);
    }
    if (!limits.isEmpty()) {
      resources.withLimits(limits);
    }
    return resources.build();
  }
}
