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

import java.util.List;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.CDP)
@Alias("P")
public class PushCliCommand extends CfCliCommand {
  @Builder
  PushCliCommand(CfCliVersion cliVersion, String cliPath, GlobalOptions globalOptions, List<String> arguments,
      PushOptions options) {
    super(cliVersion, cliPath, globalOptions, CfCliCommandType.PUSH, arguments, options);
  }

  @SuperBuilder
  public static class PushOptions implements Options {
    @Option(value = "-b") String buildPack;
    @Option(value = "-c") String startupCmd;
    @Option(value = "--docker-image") String dockerImage;
    @Option(value = "--docker-username") String dockerUsername;
    @Option(value = "--droplet") String droplet;
    @Option(value = "-f") String pathToManifest;
    @Option(value = "--health-check-type") String healthCheckType;
    @Option(value = "-i") String numberOfInstances;
    @Option(value = "-k") String diskLimit;
    @Option(value = "-m") String memoryLimit;
    @Flag(value = "--no-manifest") boolean noManifest;
    @Flag(value = "--no-route") boolean noRoute;
    @Flag(value = "--no-start") boolean noStart;
    @Option(value = "-p") String pathToApp;
    @Flag(value = "--random-route") boolean randomRoute;
    @Option(value = "-s") String stackToUse;
    @Option(value = "--vars-file") List<String> variableFilePaths;
    @Option(value = "--var") List<String> variableKeyValuePairs;
    @Option(value = "-t") String appStartTimeout;
    @Option(value = "--endpoint") String healthCheckEndpoint;
    @Flag(value = "--no-wait") boolean noWait;
    @Option(value = "--strategy") String strategy;
    @Option(value = "--task") String task;
    @Option(value = "-d") String customDomain;
    @Option(value = "--hostname") String hostname;
    @Flag(value = "--no-hostname") boolean noHostname;
    @Option(value = "--route-path") String routePath;
  }
}
