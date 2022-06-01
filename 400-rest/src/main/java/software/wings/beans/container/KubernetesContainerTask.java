/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.api.DeploymentType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.LineIterator;

/**
 * Created by brett on 3/8/17
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("KUBERNETES")
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class KubernetesContainerTask extends ContainerTask {
  @Attributes(title = "LABELS") private List<Label> labels;
  private String artifactName;

  public KubernetesContainerTask() {
    super(DeploymentType.KUBERNETES.name());
  }

  public List<Label> getLabels() {
    return labels;
  }

  public void setLabels(List<Label> labels) {
    this.labels = labels;
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
    String preamble = "# Enter your Controller YAML spec below.\n"
        + "#\n"
        + "# Supported Controllers:\n"
        + "#   ReplicationController\n"
        + "#   Deployment\n"
        + "#   ReplicaSet\n"
        + "#   StatefulSet\n"
        + "#   DaemonSet\n"
        + "#\n"
        + "# Placeholders:\n"
        + "#\n"
        + "# Required: ${DOCKER_IMAGE_NAME}\n"
        + "#   - Replaced with the Docker image name and tag\n"
        + "#\n"
        + "# Optional: ${CONTAINER_NAME}\n"
        + "#   - Replaced with a container name based on the image name\n"
        + "#\n"
        + "# Optional: ${CONFIG_MAP_NAME}\n"
        + "#   - Replaced with the ConfigMap name (same as controller name)\n"
        + "#     Config map contains all unencrypted service variables and\n"
        + "#     all unencrypted config files, unless a custom\n"
        + "#     config map is provided\n"
        + "#\n"
        + "# Optional: ${SECRET_MAP_NAME}\n"
        + "#   - Replaced with the Secret name (same as controller name)\n"
        + "#     Secret map contains all encrypted service variables and\n"
        + "#     all encrypted config files\n"
        + "#\n"
        + "# Optional: ${SECRET_NAME}\n"
        + "#   - Replaced with the name of the generated image pull\n"
        + "#     secret when pulling from a private Docker registry\n"
        + "#\n"
        + "# Harness will set the controller name, namespace,\n"
        + "# selector labels, and number of replicas.\n"
        + "#\n"
        + "# Service variables will be merged into environment\n"
        + "# variables for all containers, overriding values if\n"
        + "# the name is the same.\n"
        + "#\n"
        + "# ---\n";
    setAdvancedConfig(preamble + KubernetesContainerTaskUtils.fetchYamlConfig(this.getContainerDefinitions()));
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
          .addParam("args", "Kubernetes advanced configuration is empty.");
    }

    boolean foundImagePlaceholder = false;

    try (LineIterator lineIterator = new LineIterator(new StringReader(getAdvancedConfig()))) {
      while (lineIterator.hasNext()) {
        String line = lineIterator.nextLine();
        if (isBlank(line) || line.trim().charAt(0) == '#') {
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
              "Controller spec must have a container definition with "
                  + "${DOCKER_IMAGE_NAME} placeholder.");
    }
  }

  @Override
  public ContainerTask cloneInternal() {
    ContainerTask newContainerTask = new KubernetesContainerTask();
    copyConfigToContainerTask(newContainerTask);
    return newContainerTask;
  }

  @Override
  public void validate() {
    // Do Nothing
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
}
