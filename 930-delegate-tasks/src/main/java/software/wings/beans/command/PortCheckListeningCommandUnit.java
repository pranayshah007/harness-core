/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by peeyushaggarwal on 8/3/16.
 */
@JsonTypeName("PORT_CHECK_LISTENING")
public class PortCheckListeningCommandUnit extends ExecCommandUnit {
  /**
   * Instantiates a new Port check listening command unit.
   */
  public PortCheckListeningCommandUnit() {
    setCommandUnitType(CommandUnitType.PORT_CHECK_LISTENING);
  }

  @DefaultValue("nc -v -z -w 5 localhost 8080")
  @Override
  public String getCommandString() {
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
  @JsonTypeName("PORT_CHECK_LISTENING")
  public static class Yaml extends ExecCommandUnitAbstractYaml {
    public Yaml() {
      super(CommandUnitType.PORT_CHECK_LISTENING.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType, String workingDirectory, String scriptType, String command,
        List<TailFilePatternEntry.Yaml> filePatternEntryList) {
      super(name, CommandUnitType.PORT_CHECK_LISTENING.name(), deploymentType, workingDirectory, scriptType, command,
          filePatternEntryList);
    }
  }
}
