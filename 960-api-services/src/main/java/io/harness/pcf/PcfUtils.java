/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pcf.CfDeploymentManagerImpl.DELIMITER;
import static io.harness.pcf.model.PcfConstants.AUTOSCALING_APPS_PLUGIN_NAME;
import static io.harness.pcf.model.PcfConstants.CF_PLUGIN_HOME;
import static io.harness.pcf.model.PcfConstants.SYS_VAR_CF_PLUGIN_HOME;

import static software.wings.beans.LogHelper.color;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pcf.cfcli.CfCliCommandResolver;
import io.harness.pcf.model.CfCliVersion;

import software.wings.beans.LogColor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class PcfUtils {
  public static final String BIN_BASH = "/bin/bash";
  public static final String CF_CALL = "[CF Call info]";
  private PcfUtils() {}

  public static void logCliCommand(String command, long timeInMillis) {
    log.debug("{}: CLI, {}, timeInMillis: {}", CF_CALL, command, timeInMillis);
  }

  public static void logCliCommandFailure(String command, long timeInMillis, int exitCode, String message) {
    log.debug("{}: Failure from CLI, {}, timeInMillis: {}, exitCode: {}, message: {}", CF_CALL, command, timeInMillis,
        exitCode, message);
  }

  public static void logSdkCommand(String API, Object request, long timeInMillis) {
    log.debug("{}: SDK, {}, request: {}, timeInMillis: {}", CF_CALL, API, request, timeInMillis);
  }

  public static void logSdkCommandFailure(String API, Object request, long timeInMillis, String message) {
    log.debug("{}: Failure from SDK, {}, request: {}, timeInMillis: {}, message: {}", CF_CALL, API, request,
        timeInMillis, message);
  }

  public static boolean checkIfAppAutoscalarInstalled(final String cfCliPath, CfCliVersion cfCliVersion)
      throws PivotalClientApiException {
    boolean appAutoscalarInstalled;
    Map<String, String> map = new HashMap<>();
    map.put(CF_PLUGIN_HOME, resolvePcfPluginHome());
    String command =
        CfCliCommandResolver.getCheckingPluginsCliCommand(cfCliPath, cfCliVersion, AUTOSCALING_APPS_PLUGIN_NAME);
    ProcessExecutor processExecutor = createExecutorForAutoscalarPluginCheck(command, map);

    try {
      ProcessResult processResult = processExecutor.execute();
      appAutoscalarInstalled = isNotEmpty(processResult.outputUTF8());
    } catch (Exception e) {
      throw new PivotalClientApiException("check for AppAutoscalar plugin failed", e);
    }

    return appAutoscalarInstalled;
  }

  public static String resolvePcfPluginHome() {
    // look into java system variable
    final String sysVarPluginHome = System.getProperty(SYS_VAR_CF_PLUGIN_HOME);
    if (isNotEmpty(sysVarPluginHome)) {
      return sysVarPluginHome.trim();
    }
    // env variable
    final String envVarPluginHome = System.getenv(CF_PLUGIN_HOME);
    if (isNotEmpty(envVarPluginHome)) {
      return envVarPluginHome.trim();
    }
    // default is user home
    return System.getProperty("user.home");
  }

  public static ProcessExecutor createExecutorForAutoscalarPluginCheck(final String command, Map<String, String> map) {
    return new ProcessExecutor()
        .timeout(1, TimeUnit.MINUTES)
        .command(BIN_BASH, "-c", command)
        .readOutput(true)
        .environment(map);
  }

  public static int getRevisionFromServiceName(String name) {
    if (name != null) {
      int index = name.lastIndexOf(DELIMITER);
      if (index >= 0) {
        return getIntegerSafe(name.substring(index + DELIMITER.length()));
      }
    }
    return -1;
  }

  public static Integer getIntegerSafe(String integer) {
    try {
      return Integer.parseInt(integer);
    } catch (NumberFormatException e) {
      // Ignore
    }
    return -1;
  }

  public static String encodeColor(String appName) {
    return null == appName ? EMPTY : color(appName, LogColor.Cyan);
  }
}
