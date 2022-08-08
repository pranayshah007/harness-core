/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfcli;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pcf.cfcli.option.GlobalOptions;
import io.harness.pcf.cfcli.option.Options;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public abstract class CfCliCommand {
  private CfCliCommand() {}

  CfCliVersion cliVersion;
  String cliPath;
  GlobalOptions globalOptions;
  CfCliCommandType commandType;
  List<String> arguments;
  Options options;

  public String getCommand() {
    return CfCliCommandBuilder.buildCommand(this);
  }
}
