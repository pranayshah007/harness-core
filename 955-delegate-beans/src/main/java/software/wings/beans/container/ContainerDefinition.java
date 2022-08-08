/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.container;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.yaml.BaseYaml;

import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(CDP)
@Builder
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class ContainerDefinition {
  @Attributes(title = "PORT MAPPINGS") List<PortMapping> portMappings;
  @SchemaIgnore private String name;
  @Attributes(title = "Commands") private List<String> commands;
  @Attributes(title = "CPU", required = true) private Double cpu;
  @DefaultValue("256") @Attributes(title = "MEMORY", required = true) private Integer memory;
  @Attributes(title = "LOG CONFIGURATION") private LogConfiguration logConfiguration;
  @Attributes(title = "STORAGE/VOLUME") private List<StorageConfiguration> storageConfigurations;

  @SchemaIgnore
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getCommands() {
    return commands;
  }

  public void setCommands(List<String> commands) {
    this.commands = commands;
  }

  public LogConfiguration getLogConfiguration() {
    return logConfiguration;
  }

  public void setLogConfiguration(LogConfiguration logConfiguration) {
    this.logConfiguration = logConfiguration;
  }

  public List<StorageConfiguration> getStorageConfigurations() {
    return storageConfigurations;
  }

  public void setStorageConfigurations(List<StorageConfiguration> storageConfigurations) {
    this.storageConfigurations = storageConfigurations;
  }

  public Double getCpu() {
    return cpu;
  }

  public void setCpu(Double cpu) {
    this.cpu = cpu;
  }

  public Integer getMemory() {
    return memory;
  }

  public void setMemory(Integer memory) {
    this.memory = memory;
  }

  public List<PortMapping> getPortMappings() {
    return portMappings;
  }

  public void setPortMappings(List<PortMapping> portMappings) {
    this.portMappings = portMappings;
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class Yaml extends BaseYaml {
    List<PortMapping.Yaml> portMappings;
    private String name;
    private List<String> commands;
    private Double cpu;
    private Integer memory;
    private LogConfiguration.Yaml logConfiguration;
    private List<StorageConfiguration.Yaml> storageConfigurations;

    @Builder
    public Yaml(List<PortMapping.Yaml> portMappings, String name, List<String> commands, Double cpu, Integer memory,
        LogConfiguration.Yaml logConfiguration, List<StorageConfiguration.Yaml> storageConfigurations) {
      this.portMappings = portMappings;
      this.name = name;
      this.commands = commands;
      this.cpu = cpu;
      this.memory = memory;
      this.logConfiguration = logConfiguration;
      this.storageConfigurations = storageConfigurations;
    }
  }
}
