/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfcli.command;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pcf.cfcli.CfCliCommand;
import io.harness.pcf.cfcli.CfCliCommandType;
import io.harness.pcf.cfcli.option.Flag;
import io.harness.pcf.cfcli.option.GlobalOptions;
import io.harness.pcf.cfcli.option.Option;
import io.harness.pcf.cfcli.option.Options;
import io.harness.pcf.model.CfCliVersion;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Alias("P")
public class CurlCliCommand extends CfCliCommand {
  @Builder
  CurlCliCommand(CfCliVersion cliVersion, String cliPath, GlobalOptions globalOptions, List<String> arguments,
                 CurlOptions options) {
    super(cliVersion, cliPath, globalOptions, CfCliCommandType.CURL, arguments, options);
  }

  @SuperBuilder
  public static class CurlOptions implements Options {
    @Option(value = "-H") String customHeaders;
    @Option(value = "-X") String httpMethod;
    @Option(value = "-d") String httpData;
    @Option(value = "--output") String outputFilePath;
    @Flag(value = "--fail") boolean fail;
    @Flag(value = "-i") boolean includeResponseHeadersInOutput;
  }
}
