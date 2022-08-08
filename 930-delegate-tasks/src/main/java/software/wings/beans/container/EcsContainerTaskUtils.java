/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.container.ContainerTaskCommons.CONTAINER_NAME_PLACEHOLDER_REGEX;
import static software.wings.beans.container.ContainerTaskCommons.DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX;
import static software.wings.beans.container.ContainerTaskCommons.DUMMY_CONTAINER_NAME;
import static software.wings.beans.container.ContainerTaskCommons.DUMMY_DOCKER_IMAGE_NAME;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.strip;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.expression.RegexFunctor;
import io.harness.serializer.JsonSubtypeResolver;
import io.harness.serializer.JsonUtils;

import software.wings.utils.EcsConvention;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.services.ecs.model.HostVolumeProperties;
import com.amazonaws.services.ecs.model.MountPoint;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.TransportProtocol;
import com.amazonaws.services.ecs.model.Volume;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("ECS")
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
@UtilityClass
public class EcsContainerTaskUtils {
  private static final String DUMMY_EXECUTION_ROLE_ARN = "hv--execution-role--hv";
  private static final String EXECUTION_ROLE_PLACEHOLDER_REGEX = "\\$\\{EXECUTION_ROLE}";
  private static final Pattern commentPattern = Pattern.compile("^#.*$");
  private static final Integer DEFAULT_CONTAINER_DEFINITION_MEMORY = 1024;

  private static ObjectMapper mapperIgnoreRegisterTaskDefinitionSuperFields;
  static {
    mapperIgnoreRegisterTaskDefinitionSuperFields = new ObjectMapper();
    mapperIgnoreRegisterTaskDefinitionSuperFields.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapperIgnoreRegisterTaskDefinitionSuperFields.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapperIgnoreRegisterTaskDefinitionSuperFields.setSubtypeResolver(
        new JsonSubtypeResolver(mapperIgnoreRegisterTaskDefinitionSuperFields.getSubtypeResolver()));
    mapperIgnoreRegisterTaskDefinitionSuperFields.registerModule(new Jdk8Module());
    mapperIgnoreRegisterTaskDefinitionSuperFields.registerModule(new GuavaModule());
    mapperIgnoreRegisterTaskDefinitionSuperFields.registerModule(new JavaTimeModule());
    mapperIgnoreRegisterTaskDefinitionSuperFields.setAnnotationIntrospector(
        new RegisterTaskDefinitionSuperFieldsIgnoreIntrospector());
  }

  private static class RegisterTaskDefinitionSuperFieldsIgnoreIntrospector extends JacksonAnnotationIntrospector {
    public boolean hasIgnoreMarker(AnnotatedMember m) {
      return m.getDeclaringClass() == AmazonWebServiceRequest.class || super.hasIgnoreMarker(m);
    }
  }

  public TaskDefinition createTaskDefinition(String advancedConfig, List<ContainerDefinition> containerDefinitions,
      String containerName, String imageName, String executionRole, String domainName) {
    String configTemplate;
    if (isNotEmpty(advancedConfig)) {
      configTemplate = removeCommentsFromAdvancedConfig(advancedConfig);
    } else {
      configTemplate = fetchJsonConfig(containerDefinitions);
    }

    if (executionRole == null) {
      executionRole = "";
    }

    if (isNotEmpty(domainName)) {
      Pattern pattern = ContainerTaskCommons.compileRegexPattern(domainName);
      Matcher matcher = pattern.matcher(configTemplate);
      if (!matcher.find()) {
        imageName = domainName + "/" + imageName;
        imageName = imageName.replaceAll("//", "/");
      }
    }

    String config = configTemplate.replaceAll(DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX, imageName)
                        .replaceAll(CONTAINER_NAME_PLACEHOLDER_REGEX, containerName)
                        .replaceAll(EXECUTION_ROLE_PLACEHOLDER_REGEX, executionRole);

    config = removeEmptySecretsContainerDefinitionString(config);

    return JsonUtils.asObject(config, TaskDefinition.class);
  }

  public RegisterTaskDefinitionRequest createRegisterTaskDefinitionRequest(String advancedConfig,
      List<ContainerDefinition> containerDefinitions, String containerName, String imageName, String executionRole,
      String domainName) {
    String configTemplate;
    if (isNotEmpty(advancedConfig)) {
      configTemplate = removeCommentsFromAdvancedConfig(advancedConfig);
    } else {
      configTemplate = fetchRegisterTaskDefinitionRequestJsonConfig(containerDefinitions);
    }

    if (executionRole == null) {
      executionRole = "";
    }

    if (isNotEmpty(domainName)) {
      Pattern pattern = ContainerTaskCommons.compileRegexPattern(domainName);
      Matcher matcher = pattern.matcher(configTemplate);
      if (!matcher.find()) {
        imageName = domainName + "/" + imageName;
        imageName = imageName.replaceAll("//", "/");
      }
    }

    String config = configTemplate.replaceAll(DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX, imageName)
                        .replaceAll(CONTAINER_NAME_PLACEHOLDER_REGEX, containerName)
                        .replaceAll(EXECUTION_ROLE_PLACEHOLDER_REGEX, executionRole);

    config = removeEmptySecretsContainerDefinitionString(config);

    return JsonUtils.asObject(config, RegisterTaskDefinitionRequest.class);
  }

  public String fetchJsonConfig(List<ContainerDefinition> containerDefinitions) {
    try {
      String containerDefinitionStr = JsonUtils.asPrettyJson(createTaskDefinition(containerDefinitions))
                                          .replaceAll(DUMMY_DOCKER_IMAGE_NAME, DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX)
                                          .replaceAll(DUMMY_CONTAINER_NAME, CONTAINER_NAME_PLACEHOLDER_REGEX)
                                          .replaceAll(DUMMY_EXECUTION_ROLE_ARN, EXECUTION_ROLE_PLACEHOLDER_REGEX);

      return removeEmptySecretsContainerDefinitionString(containerDefinitionStr);
    } catch (Exception e) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, e).addParam("args", ExceptionUtils.getMessage(e));
    }
  }

  public String fetchRegisterTaskDefinitionRequestJsonConfig(List<ContainerDefinition> containerDefinitions) {
    try {
      String containerDefinitionStr =
          convertRegisterTaskDefinitionRequestAsPrettyJson(createRegisterTaskDefinitionRequest(containerDefinitions))
              .replaceAll(DUMMY_DOCKER_IMAGE_NAME, DOCKER_IMAGE_NAME_PLACEHOLDER_REGEX)
              .replaceAll(DUMMY_CONTAINER_NAME, CONTAINER_NAME_PLACEHOLDER_REGEX)
              .replaceAll(DUMMY_EXECUTION_ROLE_ARN, EXECUTION_ROLE_PLACEHOLDER_REGEX);

      return removeEmptySecretsContainerDefinitionString(containerDefinitionStr);
    } catch (Exception e) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, e).addParam("args", ExceptionUtils.getMessage(e));
    }
  }

  @VisibleForTesting
  public static String convertRegisterTaskDefinitionRequestAsPrettyJson(
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest) throws JsonProcessingException {
    return mapperIgnoreRegisterTaskDefinitionSuperFields.writerWithDefaultPrettyPrinter().writeValueAsString(
        registerTaskDefinitionRequest);
  }

  @VisibleForTesting
  public String removeEmptySecretsContainerDefinitionString(String containerDefinitionStr) {
    containerDefinitionStr =
        new RegexFunctor().replace("\\s*\"secrets\"\\s*:\\s*\\[\\s*\\],", StringUtils.EMPTY, containerDefinitionStr);
    return containerDefinitionStr;
  }

  private String removeCommentsFromAdvancedConfig(String advancedConfig) {
    if (isEmpty(advancedConfig)) {
      return advancedConfig;
    }

    return Arrays.stream(advancedConfig.split("\n"))
        .filter(line -> !commentPattern.matcher(line).matches())
        .collect(joining("\n"));
  }

  private TaskDefinition createTaskDefinition(List<ContainerDefinition> containerDefinitions) {
    Map<String, Volume> volumeMap = new HashMap<>();
    for (ContainerDefinition containerDefinition : containerDefinitions) {
      if (isNotEmpty(containerDefinition.getStorageConfigurations())) {
        for (StorageConfiguration storageConfiguration : containerDefinition.getStorageConfigurations()) {
          if (isNotBlank(storageConfiguration.getHostSourcePath())) {
            String volumeName = EcsConvention.getVolumeName(strip(storageConfiguration.getHostSourcePath()));
            Volume volume = new Volume();
            volume.setName(volumeName);
            HostVolumeProperties hostVolumeProperties = new HostVolumeProperties();
            hostVolumeProperties.setSourcePath(strip(storageConfiguration.getHostSourcePath()));
            volume.setHost(hostVolumeProperties);
            volumeMap.put(volume.getName(), volume);
          }
        }
      }
    }

    return new TaskDefinition()
        .withContainerDefinitions(
            containerDefinitions.stream()
                .map(containerDefinition
                    -> createContainerDefinition(DUMMY_DOCKER_IMAGE_NAME, DUMMY_CONTAINER_NAME, containerDefinition))
                .collect(toList()))
        .withExecutionRoleArn(DUMMY_EXECUTION_ROLE_ARN)
        .withVolumes(volumeMap.values())
        .withCpu(containerDefinitions.stream()
                     .filter(def -> def.getCpu() != null)
                     .findFirst()
                     .map(cd -> Integer.toString(cd.getCpu().intValue()))
                     .orElse(null))
        .withMemory(containerDefinitions.stream()
                        .filter(def -> def.getMemory() != null)
                        .findFirst()
                        .map(cd -> cd.getMemory().toString())
                        .orElse(null));
  }

  private RegisterTaskDefinitionRequest createRegisterTaskDefinitionRequest(
      List<ContainerDefinition> containerDefinitions) {
    Map<String, Volume> volumeMap = new HashMap<>();
    for (ContainerDefinition containerDefinition : containerDefinitions) {
      if (isNotEmpty(containerDefinition.getStorageConfigurations())) {
        for (StorageConfiguration storageConfiguration : containerDefinition.getStorageConfigurations()) {
          if (isNotBlank(storageConfiguration.getHostSourcePath())) {
            String volumeName = EcsConvention.getVolumeName(strip(storageConfiguration.getHostSourcePath()));
            Volume volume = new Volume();
            volume.setName(volumeName);
            HostVolumeProperties hostVolumeProperties = new HostVolumeProperties();
            hostVolumeProperties.setSourcePath(strip(storageConfiguration.getHostSourcePath()));
            volume.setHost(hostVolumeProperties);
            volumeMap.put(volume.getName(), volume);
          }
        }
      }
    }

    return new RegisterTaskDefinitionRequest()
        .withContainerDefinitions(
            containerDefinitions.stream()
                .map(containerDefinition
                    -> createContainerDefinition(DUMMY_DOCKER_IMAGE_NAME, DUMMY_CONTAINER_NAME, containerDefinition))
                .collect(toList()))
        .withExecutionRoleArn(DUMMY_EXECUTION_ROLE_ARN)
        .withVolumes(volumeMap.values())
        .withCpu(containerDefinitions.stream()
                     .filter(def -> def.getCpu() != null)
                     .findFirst()
                     .map(cd -> Integer.toString(cd.getCpu().intValue()))
                     .orElse(null))
        .withMemory(containerDefinitions.stream()
                        .filter(def -> def.getMemory() != null)
                        .findFirst()
                        .map(cd -> cd.getMemory().toString())
                        .orElse(null));
  }

  private com.amazonaws.services.ecs.model.ContainerDefinition createContainerDefinition(
      String imageName, String containerName, ContainerDefinition harnessContainerDefinition) {
    com.amazonaws.services.ecs.model.ContainerDefinition containerDefinition =
        new com.amazonaws.services.ecs.model.ContainerDefinition()
            .withName(strip(containerName))
            .withImage(strip(imageName));

    containerDefinition.setSecrets(null);
    if (harnessContainerDefinition.getMemory() != null && harnessContainerDefinition.getMemory() > 0) {
      containerDefinition.setMemory(harnessContainerDefinition.getMemory());
    } else {
      // Memory can not be null, so setting to default value of 1024 in advanced config.
      containerDefinition.setMemory(DEFAULT_CONTAINER_DEFINITION_MEMORY);
    }

    if (harnessContainerDefinition.getCpu() != null) {
      containerDefinition.setCpu(harnessContainerDefinition.getCpu().intValue());
    }

    if (harnessContainerDefinition.getPortMappings() != null) {
      List<com.amazonaws.services.ecs.model.PortMapping> portMappings =
          harnessContainerDefinition.getPortMappings()
              .stream()
              .map(portMapping
                  -> new com.amazonaws.services.ecs.model.PortMapping()
                         .withContainerPort(portMapping.getContainerPort())
                         .withHostPort(portMapping.getHostPort())
                         .withProtocol(TransportProtocol.Tcp))
              .collect(toList());
      containerDefinition.setPortMappings(portMappings);
    }

    List<String> commands = Optional.ofNullable(harnessContainerDefinition.getCommands())
                                .orElse(emptyList())
                                .stream()
                                .filter(StringUtils::isNotBlank)
                                .map(StringUtils::strip)
                                .collect(toList());
    containerDefinition.setCommand(commands);

    if (harnessContainerDefinition.getLogConfiguration() != null) {
      LogConfiguration harnessLogConfiguration = harnessContainerDefinition.getLogConfiguration();
      if (isNotBlank(harnessLogConfiguration.getLogDriver())) {
        com.amazonaws.services.ecs.model.LogConfiguration logConfiguration =
            new com.amazonaws.services.ecs.model.LogConfiguration().withLogDriver(
                strip(harnessLogConfiguration.getLogDriver()));
        Optional.ofNullable(harnessLogConfiguration.getOptions())
            .orElse(emptyList())
            .forEach(
                logOption -> logConfiguration.addOptionsEntry(strip(logOption.getKey()), strip(logOption.getValue())));
        containerDefinition.setLogConfiguration(logConfiguration);
      }
    }

    if (isNotEmpty(harnessContainerDefinition.getStorageConfigurations())) {
      List<StorageConfiguration> harnessStorageConfigurations = harnessContainerDefinition.getStorageConfigurations();
      containerDefinition.setMountPoints(
          harnessStorageConfigurations.stream()
              .filter(storageConfiguration -> isNotBlank(storageConfiguration.getContainerPath()))
              .map(storageConfiguration
                  -> new MountPoint()
                         .withContainerPath(strip(storageConfiguration.getContainerPath()))
                         .withSourceVolume(EcsConvention.getVolumeName(strip(storageConfiguration.getHostSourcePath())))
                         .withReadOnly(storageConfiguration.isReadonly()))
              .collect(toList()));
    }

    return containerDefinition;
  }
}
