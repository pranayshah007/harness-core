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
 * Created by anubhaw on 7/14/16.
 */
@JsonTypeName("PROCESS_CHECK_STOPPED")
public class ProcessCheckStoppedCommandUnit extends ExecCommandUnit {
  /**
   * Instantiates a new Process check command unit.
   */
  public ProcessCheckStoppedCommandUnit() {
    setCommandUnitType(CommandUnitType.PROCESS_CHECK_STOPPED);
  }

  @SchemaIgnore
  @Override
  public String getCommandPath() {
    return super.getCommandPath();
  }

  @Attributes(title = "Command")
  @DefaultValue("pgrep -f \"\\-Dcatalina.home=$WINGS_RUNTIME_PATH/tomcat\"\nrc=$?"
      + "\nif [ \"$rc\" -eq 0 ]\nthen\nexit 1\nfi")
  @Override
  public String
  getCommandString() {
    return super.getCommandString();
  }

  @SchemaIgnore
  @Override
  public List<TailFilePatternEntry> getTailPatterns() {
    return super.getTailPatterns();
  }

  @SchemaIgnore
  @Override
  public String getPreparedCommand() {
    return super.getPreparedCommand();
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("PROCESS_CHECK_STOPPED")
  public static class Yaml extends ExecCommandUnitAbstractYaml {
    public Yaml() {
      super(CommandUnitType.PROCESS_CHECK_STOPPED.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType, String workingDirectory, String scriptType, String command,
        List<TailFilePatternEntry.Yaml> filePatternEntryList) {
      super(name, CommandUnitType.PROCESS_CHECK_STOPPED.name(), deploymentType, workingDirectory, scriptType, command,
          filePatternEntryList);
    }
  }
}
