/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf.cfcli;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pcf.cfcli.command.ApiCliCommand;
import io.harness.pcf.cfcli.command.ApiCliCommand.ApiOptions;
import io.harness.pcf.cfcli.command.AuthCliCommand;
import io.harness.pcf.cfcli.command.AutoscalingAppsCliCommand;
import io.harness.pcf.cfcli.command.ConfigureAutoscalingCliCommand;
import io.harness.pcf.cfcli.command.DisableAutoscalingCliCommand;
import io.harness.pcf.cfcli.command.EnableAutoscalingCliCommand;
import io.harness.pcf.cfcli.command.LogsCliCommand;
import io.harness.pcf.cfcli.command.MapRouteCliCommand;
import io.harness.pcf.cfcli.command.MapRouteCliCommand.MapRouteOptions;
import io.harness.pcf.cfcli.command.PluginsCliCommand;
import io.harness.pcf.cfcli.command.PushCliCommand;
import io.harness.pcf.cfcli.command.PushCliCommand.PushOptions;
import io.harness.pcf.cfcli.command.SetEnvCliCommand;
import io.harness.pcf.cfcli.command.StartAppCliCommand;
import io.harness.pcf.cfcli.command.TargetCliCommand;
import io.harness.pcf.cfcli.command.TargetCliCommand.TargetOptions;
import io.harness.pcf.cfcli.command.UnmapRouteCliCommand;
import io.harness.pcf.cfcli.command.UnmapRouteCliCommand.UnmapRouteOptions;
import io.harness.pcf.cfcli.command.UnsetEnvCliCommand;
import io.harness.pcf.model.CfCliVersion;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface CfCliCommandResolver {
  String GREP = "| grep";

  static String getApiCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String endpoint, boolean skipSslValidation) {
    return ApiCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Collections.singletonList(endpoint))
        .options(ApiOptions.builder().skipSslValidation(skipSslValidation).build())
        .build()
        .getCommand();
  }

  static String getAuthCommand(final String cfCliPath, CfCliVersion cfCliVersion) {
    return AuthCliCommand.builder().cliPath(cfCliPath).cliVersion(cfCliVersion).build().getCommand();
  }

  static String getTargetCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String org, final String space) {
    return TargetCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .options(TargetOptions.builder().org(org).space(space).build())
        .build()
        .getCommand();
  }

  static String getSetEnvCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String appName,
      final String envName, final String envValue) {
    return SetEnvCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, envName, envValue))
        .build()
        .getCommand();
  }

  static String getUnsetEnvCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName, final String envName) {
    return UnsetEnvCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, envName))
        .build()
        .getCommand();
  }

  static String getLogsCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String appName) {
    return LogsCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Collections.singletonList(appName))
        .build()
        .getCommand();
  }

  static String getUnmapRouteCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName, final String domain, final String port) {
    return UnmapRouteCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, domain))
        .options(UnmapRouteOptions.builder().port(port).build())
        .build()
        .getCommand();
  }

  static String getUnmapRouteCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String appName,
      final String domain, String hostname, final String path) {
    return UnmapRouteCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, domain))
        .options(UnmapRouteOptions.builder().hostname(hostname).path(path).build())
        .build()
        .getCommand();
  }

  static String getMapRouteCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName, final String domain, final String port) {
    return MapRouteCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, domain))
        .options(MapRouteOptions.builder().port(port).build())
        .build()
        .getCommand();
  }

  static String getMapRouteCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String appName,
      final String domain, String hostname, final String path) {
    return MapRouteCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, domain))
        .options(MapRouteOptions.builder().hostname(hostname).path(path).build())
        .build()
        .getCommand();
  }

  static String getConfigureAutoscalingCliCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName, final String autoScalarFilePath) {
    return ConfigureAutoscalingCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Arrays.asList(appName, autoScalarFilePath))
        .build()
        .getCommand();
  }

  static String getStartAppCliCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String appName) {
    return StartAppCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Collections.singletonList(appName))
        .build()
        .getCommand();
  }

  static String getDisableAutoscalingCliCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName) {
    return DisableAutoscalingCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Collections.singletonList(appName))
        .build()
        .getCommand();
  }

  static String getEnableAutoscalingCliCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName) {
    return EnableAutoscalingCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(Collections.singletonList(appName))
        .build()
        .getCommand();
  }

  static String getAutoscalingAppsCliCommandWithGrep(
      final String cfCliPath, CfCliVersion cfCliVersion, final String appName) {
    List<String> arguments = new LinkedList<>();
    arguments.add(GREP);
    arguments.add(appName);

    return AutoscalingAppsCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(arguments)
        .build()
        .getCommand();
  }

  static String getPushCliCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String pathToManifest) {
    return PushCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .options(PushOptions.builder().pathToManifest(pathToManifest).build())
        .build()
        .getCommand();
  }

  static String getPushCliCommand(final String cfCliPath, CfCliVersion cfCliVersion, final String pathToManifest,
      final List<String> variableFilePaths) {
    return PushCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .options(PushOptions.builder().pathToManifest(pathToManifest).variableFilePaths(variableFilePaths).build())
        .build()
        .getCommand();
  }

  static String getCheckingPluginsCliCommand(
      final String cfCliPath, CfCliVersion cfCliVersion, final String pluginName) {
    List<String> arguments = new LinkedList<>();
    arguments.add(GREP);
    arguments.add(pluginName);

    return PluginsCliCommand.builder()
        .cliPath(cfCliPath)
        .cliVersion(cfCliVersion)
        .arguments(arguments)
        .build()
        .getCommand();
  }
}
