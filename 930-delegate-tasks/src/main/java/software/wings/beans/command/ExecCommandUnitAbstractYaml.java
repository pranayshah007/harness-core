/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TargetModule(HarnessModule._955_CG_YAML)
@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDC)
public class ExecCommandUnitAbstractYaml extends SshCommandUnit.Yaml {
  // maps to commandPath
  private String workingDirectory;
  private String scriptType;
  // maps to commandString
  private String command;
  // maps to tailPatterns
  private List<TailFilePatternEntry.Yaml> filePatternEntryList;

  public ExecCommandUnitAbstractYaml(String commandUnitType) {
    super(commandUnitType);
  }

  public ExecCommandUnitAbstractYaml(String name, String commandUnitType, String deploymentType,
      String workingDirectory, String scriptType, String command,
      List<TailFilePatternEntry.Yaml> filePatternEntryList) {
    super(name, commandUnitType, deploymentType);
    this.workingDirectory = workingDirectory;
    this.scriptType = scriptType;
    this.command = command;
    this.filePatternEntryList = filePatternEntryList;
  }
}
