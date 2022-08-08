/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.dto;

import software.wings.beans.container.ContainerDefinition;

import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@SuperBuilder
@Data
public abstract class ContainerTask {
  @Setter(AccessLevel.NONE) @NotEmpty private String deploymentType;
  @NotEmpty private String serviceId;
  @Setter(AccessLevel.NONE) private String advancedConfig;
  private List<ContainerDefinition> containerDefinitions;

  public void setAdvancedConfig(String advancedConfig) {
    this.advancedConfig = trimYaml(advancedConfig);
  }

  // for now copied from software.wings.yaml.YamlHelper.trimYaml ...
  private static String trimYaml(String yamlString) {
    return yamlString == null ? null : yamlString.replaceAll("\\s*\\n", "\n");
  }

  // Define parts of builder ourselves to ensure advanced config is trimmed properly even via builder.
  public abstract static class ContainerTaskBuilder<C extends ContainerTask, B extends ContainerTaskBuilder<C, B>> {
    public B advancedConfig(final String advancedConfig) {
      this.advancedConfig = trimYaml(advancedConfig);
      return this.self();
    }
  }
}