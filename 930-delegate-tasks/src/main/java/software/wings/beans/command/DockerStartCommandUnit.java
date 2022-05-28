/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by anubhaw on 1/4/17.
 */
@JsonTypeName("DOCKER_START")
public class DockerStartCommandUnit extends ExecCommandUnit {
  /**
   * Instantiates a new Docker run command unit.
   */
  public DockerStartCommandUnit() {
    setArtifactNeeded(true);
    setCommandUnitType(CommandUnitType.DOCKER_START);
  }

  @Attributes(title = "Command")
  @DefaultValue(
      "docker login --username=\"$DOCKER_USER_ID\" --password=\"$DOCKER_USER_PASSWORD\"\ndocker run -d -it \"$DOCKER_IMAGE\"\ndocker logout")
  @Override
  public String
  getCommandString() {
    return super.getCommandString();
  }

  @SchemaIgnore
  @Override
  public String getCommandPath() {
    return super.getCommandPath();
  }

  @SchemaIgnore
  @Override
  public String getPreparedCommand() {
    return super.getPreparedCommand();
  }

  @SchemaIgnore
  @Override
  public List<TailFilePatternEntry> getTailPatterns() {
    return super.getTailPatterns();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("DOCKER_START")
  public static class Yaml extends ExecCommandUnitAbstractYaml {
    public Yaml() {
      super(CommandUnitType.DOCKER_START.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType, String workingDirectory, String scriptType, String command,
        List<TailFilePatternEntry.Yaml> filePatternEntryList) {
      super(name, CommandUnitType.DOCKER_START.name(), deploymentType, workingDirectory, scriptType, command,
          filePatternEntryList);
    }
  }
}
