/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.litek8s;

import io.harness.delegate.core.beans.ResourceRequirements;
import io.harness.delegate.core.beans.SecurityContext;
import io.harness.delegate.core.beans.StepRuntime;
import io.harness.delegate.service.core.k8s.K8SEnvVar;
import io.harness.delegate.service.core.util.K8SResourceHelper;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
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

public class ContainerBuilder {
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

  private static final String WORKING_DIR = "/harness";
  private static final String ADDON_RUN_COMMAND = "/addon/bin/ci-addon";
  private static final String ADDON_RUN_ARGS_FORMAT = "--port %s";
  public static final int RESERVED_LE_PORT = 20001;

  public V1ContainerBuilder createContainer(final String taskId, final StepRuntime containerRuntime, final int port) {
    final V1ContainerBuilder containerBuilder = new V1ContainerBuilder()
                                                    .withName(K8SResourceHelper.getContainerName(taskId))
                                                    .withImage(containerRuntime.getUses())
                                                    .withCommand(ADDON_RUN_COMMAND)
                                                    .withArgs(String.format(ADDON_RUN_ARGS_FORMAT, port))
                                                    .withPorts(getPort(port))
                                                    .withEnv(K8SEnvVar.fromMap(containerRuntime.getEnvMap()))
                                                    .withResources(getResources("100m", "100Mi"))
                                                    .withImagePullPolicy("Always");

    if (containerRuntime.hasResource()) {
      containerBuilder.withResources(
          getResources(containerRuntime.getResource().getCpu(), containerRuntime.getResource().getMemory()));
    }

    if (containerRuntime.hasSecurityContext()) {
      final boolean isPrivilegedImage = isPrivilegedImage(containerRuntime.getUses());
      containerBuilder.withSecurityContext(
          getSecurityContext(containerRuntime.getSecurityContext(), isPrivilegedImage));
    }

    if (Strings.isNullOrEmpty(containerRuntime.getWorkingDir())) {
      containerBuilder.withWorkingDir(containerRuntime.getWorkingDir());
    }

    return containerBuilder;
  }

  public V1ContainerBuilder createAddonInitContainer() {
    return new V1ContainerBuilder()
        .withName(SETUP_ADDON_CONTAINER_NAME)
        .withImage(getAddonImage())
        .withCommand(getAddonCmd()) // TODO: Why defining here, should be part of image
        .withArgs(getAddonArgs()) // TODO: Why defining here, should be part of image
        .withEnv(new V1EnvVar().name(HARNESS_WORKSPACE).value(WORKING_DIR))
        .withImagePullPolicy("Always")
        .withResources(getResources("100m", "100Mi"));
  }

  public V1ContainerBuilder createLEContainer(final ResourceRequirements resource) {
    // TODO: See how to share ports to LE, possibly env var?
    return new V1ContainerBuilder()
        .withName(LE_CONTAINER_NAME)
        .withImage(getLeImage())
        .withEnv(K8SEnvVar.fromMap(getLeEnvVars()))
        .withImagePullPolicy("Always")
        .withPorts(getPort(RESERVED_LE_PORT))
        .withResources(getResources(resource.getCpu(), resource.getMemory()))
        .withWorkingDir(WORKING_DIR);
  }

  private Map<String, String> getLeEnvVars() {
    final var envVars = ImmutableMap.<String, String>builder();
    envVars.put(HARNESS_WORKSPACE, ContainerBuilder.WORKING_DIR);
    envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    envVars.put(HARNESS_LE_STATUS_REST_ENABLED, "true");
    envVars.put(DELEGATE_SERVICE_ENDPOINT_VARIABLE, "delegate-service"); // Todo: make per delegate
    envVars.put(DELEGATE_SERVICE_ID_VARIABLE, "delegate-grpc-service"); // fixme: What's this for?
    envVars.put(
        HARNESS_ACCOUNT_ID_VARIABLE, "kmpySmUISimoRrJL6NL73w"); // TODO: How do we get these mandatory fields to runner
    //    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    //    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    //    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    //    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    //    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    //    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    return envVars.build();
  }

  @NonNull
  private List<String> getAddonCmd() {
    return List.of("sh", "-c", "--");
  }

  @NonNull
  private List<String> getAddonArgs() {
    return List.of(
        "mkdir -p /addon/bin; mkdir -p /addon/tmp; chmod -R 776 /addon/tmp; if [ -e /usr/local/bin/ci-addon-linux-amd64 ];then cp /usr/local/bin/ci-addon-linux-amd64 /addon/bin/ci-addon;else cp /usr/local/bin/ci-addon-linux /addon/bin/ci-addon;fi; chmod +x /addon/bin/ci-addon; cp /usr/local/bin/tmate /addon/bin/tmate; chmod +x /addon/bin/tmate; cp /usr/local/bin/java-agent.jar /addon/bin/java-agent.jar; chmod +x /addon/bin/java-agent.jar; if [ -e /usr/local/bin/split_tests ];then cp /usr/local/bin/split_tests /addon/bin/split_tests; chmod +x /addon/bin/split_tests; export PATH=$PATH:/addon/bin; fi;");
  }

  @NonNull
  // TODO: possible to pull from private repo or have CI/CD/STO specific addon (CI Addon has TI & STO specific code).
  // It should belong to runner startup config not PL because it's a property of a runner not our SaaS system, but that
  // means you need to reconfigure runner to upgrade. Maybe through upgrade (e.g. put in config map that upgrader
  // updates)? Extra considerations for imagePullSecrets for private repos
  private String getAddonImage() {
    return "harness/ci-addon:1.16.7";
  }

  @NonNull
  private String getLeImage() {
    return "harness/ci-lite-engine:1.16.7"; // TODO: Same as for Addon image
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

  // TODO: Make sure LE has max(all) resources and other containers min resources (runner should handle that not
  // manager)
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
