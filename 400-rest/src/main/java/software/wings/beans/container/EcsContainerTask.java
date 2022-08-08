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
import static io.harness.exception.WingsException.USER;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.api.DeploymentType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.io.LineIterator;
/**
 * Created by anubhaw on 2/6/17.
 */
@OwnedBy(CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("ECS")
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class EcsContainerTask extends ContainerTask {
  static final String PREAMBLE = "# Enter your Task Definition JSON spec below.\n"
      + "#\n"
      + "# Placeholders:\n"
      + "#\n"
      + "# Required: ${DOCKER_IMAGE_NAME}\n"
      + "#   - Replaced with the Docker image name and tag\n"
      + "#\n"
      + "# Optional: ${CONTAINER_NAME}\n"
      + "#   - Replaced with a container name based on the image name\n"
      + "#\n"
      + "# Required For Fargate: ${EXECUTION_ROLE}\n"
      + "#   - Replaced with execution role arn\n"
      + "#\n"
      + "# Harness will set the task family of the task definition.\n"
      + "#\n"
      + "# Service variables will be merged into environment\n"
      + "# variables for all containers, overriding values if\n"
      + "# the name is the same.\n"
      + "#\n"
      + "# ---\n";

  private String artifactName;

  public EcsContainerTask() {
    super(DeploymentType.ECS.name());
  }

  public String getArtifactName() {
    return artifactName;
  }

  public void setArtifactName(String artifactName) {
    this.artifactName = artifactName;
  }

  @Override
  @SchemaIgnore
  public String getServiceId() {
    return super.getServiceId();
  }

  @Override
  public ContainerTask convertToAdvanced() {
    setAdvancedConfig(PREAMBLE + EcsContainerTaskUtils.fetchJsonConfig(this.getContainerDefinitions()));
    return this;
  }

  public ContainerTask convertToAdvanced(boolean ecsRegisterTaskDefinitionTagsEnabled) {
    if (ecsRegisterTaskDefinitionTagsEnabled) {
      setAdvancedConfig(PREAMBLE
          + EcsContainerTaskUtils.fetchRegisterTaskDefinitionRequestJsonConfig(this.getContainerDefinitions()));
    } else {
      setAdvancedConfig(PREAMBLE + EcsContainerTaskUtils.fetchJsonConfig(this.getContainerDefinitions()));
    }
    return this;
  }

  @Override
  public ContainerTask convertFromAdvanced() {
    setAdvancedConfig(null);
    return this;
  }

  @Override
  public void validateAdvanced() {
    // Instantiating doesn't work when service variable expressions are used so only check for placeholder
    if (isEmpty(getAdvancedConfig())) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
          .addParam("args", "ECS advanced configuration is empty.");
    }

    boolean foundImagePlaceholder = false;

    try (LineIterator lineIterator = new LineIterator(new StringReader(getAdvancedConfig()))) {
      while (lineIterator.hasNext()) {
        String line = lineIterator.nextLine();
        if (line.trim().charAt(0) == '#') {
          continue;
        }

        if (line.contains("${DOCKER_IMAGE_NAME}")) {
          foundImagePlaceholder = true;
        }
      }
    } catch (IOException ignore) {
      foundImagePlaceholder = false;
    }

    if (!foundImagePlaceholder) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, USER)
          .addParam("args",
              "Task definition spec must have a container definition with "
                  + "${DOCKER_IMAGE_NAME} placeholder.");
    }
  }

  @Override
  public ContainerTask cloneInternal() {
    ContainerTask newContainerTask = new EcsContainerTask();
    copyConfigToContainerTask(newContainerTask);
    return newContainerTask;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends ContainerTask.Yaml {
    @Builder
    public Yaml(
        String type, String harnessApiVersion, String advancedConfig, ContainerDefinition.Yaml containerDefinition) {
      super(type, harnessApiVersion, advancedConfig, containerDefinition);
    }
  }

  @Override
  public void validate() {
    if (isNotEmpty(getAdvancedConfig())) {
      return;
    }

    List<ContainerDefinition> containerDefinitions = getContainerDefinitions();
    for (ContainerDefinition containerDefinition : containerDefinitions) {
      List<PortMapping> portMappings = containerDefinition.getPortMappings();
      if (isNotEmpty(portMappings)) {
        List<PortMapping> portMappingList =
            portMappings.stream().filter(portMapping -> portMapping.getContainerPort() != null).collect(toList());
        containerDefinition.setPortMappings(portMappingList);
      }
    }
  }
}
