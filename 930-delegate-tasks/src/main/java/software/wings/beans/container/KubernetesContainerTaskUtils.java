/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.KubernetesHelperService.toYaml;

import static software.wings.beans.container.ContainerTaskCommons.CONTAINER_NAME_PLACEHOLDER_REGEX;
import static software.wings.beans.container.ContainerTaskCommons.DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX;
import static software.wings.beans.container.ContainerTaskCommons.DUMMY_CONTAINER_NAME;
import static software.wings.beans.container.ContainerTaskCommons.DUMMY_DOCKER_IMAGE_NAME;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.strip;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.k8s.KubernetesConvention;

import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.HostPathVolumeSource;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.kubernetes.api.model.extensions.DeploymentBuilder;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KubernetesContainerTaskUtils {
  public static final String CONFIG_MAP_NAME_PLACEHOLDER_REGEX = "\\$\\{CONFIG_MAP_NAME}";
  public static final String SECRET_MAP_NAME_PLACEHOLDER_REGEX = "\\$\\{SECRET_MAP_NAME}";

  private static final String REGISTRY_SECRET_NAME_PLACEHOLDER_REGEX = "\\$\\{SECRET_NAME}";
  private static final String DUMMY_SECRET_NAME = "hv--secret-name--hv";
  private static final Pattern DAEMON_SET_PATTERN = Pattern.compile("kind:\\s*\"?DaemonSet");
  private static final Pattern STATEFUL_SET_PATTERN = Pattern.compile("kind:\\s*\"?StatefulSet");

  public boolean checkDaemonSet(String advancedConfig) {
    return isNotBlank(advancedConfig) && DAEMON_SET_PATTERN.matcher(advancedConfig).find();
  }

  public boolean checkStatefulSet(String advancedConfig) {
    return isNotBlank(advancedConfig) && STATEFUL_SET_PATTERN.matcher(advancedConfig).find();
  }

  public HasMetadata createController(String advancedConfig, List<ContainerDefinition> containerDefinitions,
      String containerName, String imageNameTag, String registrySecretName, String configMapName, String secretMapName,
      String domainName) {
    try {
      String configTemplate = isNotBlank(advancedConfig) ? advancedConfig : fetchYamlConfig(containerDefinitions);

      if (isNotEmpty(domainName)) {
        Pattern pattern = ContainerTaskCommons.compileRegexPattern(domainName);
        Matcher matcher = pattern.matcher(configTemplate);
        if (!matcher.find()) {
          imageNameTag = domainName + "/" + imageNameTag;
          imageNameTag = imageNameTag.replaceAll("//", "/");
        }
      }

      String controllerYaml = configTemplate.replaceAll(DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX, imageNameTag)
                                  .replaceAll(CONTAINER_NAME_PLACEHOLDER_REGEX, containerName)
                                  .replaceAll(REGISTRY_SECRET_NAME_PLACEHOLDER_REGEX, registrySecretName)
                                  .replaceAll(CONFIG_MAP_NAME_PLACEHOLDER_REGEX, configMapName)
                                  .replaceAll(SECRET_MAP_NAME_PLACEHOLDER_REGEX, secretMapName);
      HasMetadata controller = KubernetesHelper.loadYaml(controllerYaml);
      if (controller == null) {
        throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
            .addParam("args", "Couldn't parse Controller YAML: " + controllerYaml);
      }
      return controller;
    } catch (Exception e) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, e).addParam("args", ExceptionUtils.getMessage(e));
    }
  }

  public String fetchYamlConfig(List<ContainerDefinition> containerDefinitions) {
    try {
      return toYaml(createDeployment(containerDefinitions))
          .replaceAll(DUMMY_DOCKER_IMAGE_NAME, DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX)
          .replaceAll(DUMMY_CONTAINER_NAME, CONTAINER_NAME_PLACEHOLDER_REGEX)
          .replaceAll(DUMMY_SECRET_NAME, REGISTRY_SECRET_NAME_PLACEHOLDER_REGEX);
    } catch (IOException e) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, e).addParam("args", ExceptionUtils.getMessage(e));
    }
  }

  private Deployment createDeployment(List<ContainerDefinition> containerDefinitions) {
    Map<String, Volume> volumeMap = new HashMap<>();
    for (ContainerDefinition containerDefinition : containerDefinitions) {
      if (containerDefinition.getStorageConfigurations() != null) {
        for (StorageConfiguration storageConfiguration : containerDefinition.getStorageConfigurations()) {
          if (isNotBlank(storageConfiguration.getHostSourcePath())) {
            String volumeName = KubernetesConvention.getVolumeName(strip(storageConfiguration.getHostSourcePath()));
            volumeMap.put(volumeName,
                new VolumeBuilder()
                    .withName(volumeName)
                    .withHostPath(new HostPathVolumeSource(strip(storageConfiguration.getHostSourcePath()), null))
                    .build());
          }
        }
      }
    }

    return new DeploymentBuilder()
        .withNewMetadata()
        .endMetadata()
        .withNewSpec()
        .withReplicas(0)
        .withNewTemplate()
        .withNewMetadata()
        .endMetadata()
        .withNewSpec()
        .addNewImagePullSecret(DUMMY_SECRET_NAME)
        .addToContainers(containerDefinitions.stream()
                             .map(KubernetesContainerTaskUtils::createContainerDefinition)
                             .toArray(Container[] ::new))
        .addToVolumes(volumeMap.values().toArray(new Volume[0]))
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  /**
   * Creates container definition
   */
  private Container createContainerDefinition(ContainerDefinition wingsContainerDefinition) {
    ContainerBuilder containerBuilder =
        new ContainerBuilder().withName(DUMMY_CONTAINER_NAME).withImage(DUMMY_DOCKER_IMAGE_NAME);

    Map<String, Quantity> limits = new HashMap<>();
    if (wingsContainerDefinition.getCpu() != null) {
      limits.put("cpu", new Quantity(wingsContainerDefinition.getCpu().toString()));
    }

    if (wingsContainerDefinition.getMemory() != null) {
      limits.put("memory", new Quantity(wingsContainerDefinition.getMemory() + "Mi"));
    }

    if (!limits.isEmpty()) {
      containerBuilder.withNewResources().withLimits(limits).withRequests(limits).endResources();
    }

    if (wingsContainerDefinition.getPortMappings() != null) {
      wingsContainerDefinition.getPortMappings().forEach(portMapping
          -> containerBuilder.addNewPort()
                 .withContainerPort(portMapping.getContainerPort())
                 .withHostPort(portMapping.getHostPort())
                 .withProtocol("TCP")
                 .endPort());
    }

    if (wingsContainerDefinition.getCommands() != null) {
      wingsContainerDefinition.getCommands().forEach(command -> {
        if (!command.trim().isEmpty()) {
          containerBuilder.withCommand(command.trim());
        }
      });
    }

    if (wingsContainerDefinition.getStorageConfigurations() != null) {
      wingsContainerDefinition.getStorageConfigurations()
          .stream()
          .filter(storageConfiguration
              -> isNotBlank(storageConfiguration.getHostSourcePath())
                  && isNotBlank(storageConfiguration.getContainerPath()))
          .forEach(storageConfiguration
              -> containerBuilder.addNewVolumeMount()
                     .withName(KubernetesConvention.getVolumeName(strip(storageConfiguration.getHostSourcePath())))
                     .withMountPath(strip(storageConfiguration.getContainerPath()))
                     .withReadOnly(storageConfiguration.isReadonly())
                     .endVolumeMount());
    }

    return containerBuilder.build();
  }
}
