/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf.cfcli;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pcf.cfcli.resolver.CfCliCommandResolver;
import io.harness.pcf.model.CfCliVersion;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CfCliCommandResolverTest extends CategoryTest {
  private static final CfCliVersion cfCliVersionV6 = CfCliVersion.V6;
  private static final String cfCliPathV6 = "/path-to-cf-cli6/cf";

  private static final CfCliVersion cfCliVersionV7 = CfCliVersion.V7;
  private static final String cfCliPathV7 = "/path-to-cf-cli7/cf7";

  private static final String cfCliDefault = "cf";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetApiCommand() {
    String endpoint = "http://domain.subdomain.com/api-endpoint";
    String apiCommand = CfCliCommandResolver.getApiCommand(cfCliPathV6, cfCliVersionV6, endpoint, true);

    assertThat(apiCommand).isNotEmpty();
    assertThat(apiCommand)
        .isEqualTo("/path-to-cf-cli6/cf api http://domain.subdomain.com/api-endpoint --skip-ssl-validation");

    apiCommand = CfCliCommandResolver.getApiCommand(cfCliPathV7, cfCliVersionV7, endpoint, true);

    assertThat(apiCommand).isNotEmpty();
    assertThat(apiCommand)
        .isEqualTo("/path-to-cf-cli7/cf7 api http://domain.subdomain.com/api-endpoint --skip-ssl-validation");

    apiCommand = CfCliCommandResolver.getApiCommand(cfCliDefault, cfCliVersionV7, endpoint, true);

    assertThat(apiCommand).isNotEmpty();
    assertThat(apiCommand).isEqualTo("cf api http://domain.subdomain.com/api-endpoint --skip-ssl-validation");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAuthCommand() {
    String authCommand = CfCliCommandResolver.getAuthCommand(cfCliPathV6, cfCliVersionV6);

    assertThat(authCommand).isNotEmpty();
    assertThat(authCommand).isEqualTo("/path-to-cf-cli6/cf auth");

    authCommand = CfCliCommandResolver.getAuthCommand(cfCliPathV7, cfCliVersionV7);

    assertThat(authCommand).isNotEmpty();
    assertThat(authCommand).isEqualTo("/path-to-cf-cli7/cf7 auth");

    authCommand = CfCliCommandResolver.getAuthCommand(cfCliDefault, cfCliVersionV7);

    assertThat(authCommand).isNotEmpty();
    assertThat(authCommand).isEqualTo("cf auth");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetTargetCommand() {
    String org = "devtest-org";
    String space = "devtest-space";
    String targetCommand = CfCliCommandResolver.getTargetCommand(cfCliPathV6, cfCliVersionV6, org, space);

    assertThat(targetCommand).isNotEmpty();
    assertThat(targetCommand).isEqualTo("/path-to-cf-cli6/cf target -o devtest-org -s devtest-space");

    targetCommand = CfCliCommandResolver.getTargetCommand(cfCliPathV7, cfCliVersionV7, org, space);

    assertThat(targetCommand).isNotEmpty();
    assertThat(targetCommand).isEqualTo("/path-to-cf-cli7/cf7 target -o devtest-org -s devtest-space");

    targetCommand = CfCliCommandResolver.getTargetCommand(cfCliDefault, cfCliVersionV7, org, space);

    assertThat(targetCommand).isNotEmpty();
    assertThat(targetCommand).isEqualTo("cf target -o devtest-org -s devtest-space");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSetEnvCommand() {
    String appName = "app-name";
    String key = "env-key";
    String value = "env-value";
    String setEnvCommand = CfCliCommandResolver.getSetEnvCommand(cfCliPathV6, cfCliVersionV6, appName, key, value);

    assertThat(setEnvCommand).isNotEmpty();
    assertThat(setEnvCommand).isEqualTo("/path-to-cf-cli6/cf set-env app-name env-key env-value");

    setEnvCommand = CfCliCommandResolver.getSetEnvCommand(cfCliPathV7, cfCliVersionV7, appName, key, value);

    assertThat(setEnvCommand).isNotEmpty();
    assertThat(setEnvCommand).isEqualTo("/path-to-cf-cli7/cf7 set-env app-name env-key env-value");

    setEnvCommand = CfCliCommandResolver.getSetEnvCommand(cfCliDefault, cfCliVersionV7, appName, key, value);

    assertThat(setEnvCommand).isNotEmpty();
    assertThat(setEnvCommand).isEqualTo("cf set-env app-name env-key env-value");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetUnsetEnvCommand() {
    String appName = "app-name";
    String envName = "env-name";
    String unsetEnvCommand = CfCliCommandResolver.getUnsetEnvCommand(cfCliPathV6, cfCliVersionV6, appName, envName);

    assertThat(unsetEnvCommand).isNotEmpty();
    assertThat(unsetEnvCommand).isEqualTo("/path-to-cf-cli6/cf unset-env app-name env-name");

    unsetEnvCommand = CfCliCommandResolver.getUnsetEnvCommand(cfCliPathV7, cfCliVersionV7, appName, envName);

    assertThat(unsetEnvCommand).isNotEmpty();
    assertThat(unsetEnvCommand).isEqualTo("/path-to-cf-cli7/cf7 unset-env app-name env-name");

    unsetEnvCommand = CfCliCommandResolver.getUnsetEnvCommand(cfCliDefault, cfCliVersionV7, appName, envName);

    assertThat(unsetEnvCommand).isNotEmpty();
    assertThat(unsetEnvCommand).isEqualTo("cf unset-env app-name env-name");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetLogsCommand() {
    String appName = "app-name";
    String logsCommand = CfCliCommandResolver.getLogsCommand(cfCliPathV6, cfCliVersionV6, appName);

    assertThat(logsCommand).isNotEmpty();
    assertThat(logsCommand).isEqualTo("/path-to-cf-cli6/cf logs app-name");

    logsCommand = CfCliCommandResolver.getLogsCommand(cfCliPathV7, cfCliVersionV7, appName);

    assertThat(logsCommand).isNotEmpty();
    assertThat(logsCommand).isEqualTo("/path-to-cf-cli7/cf7 logs app-name");

    logsCommand = CfCliCommandResolver.getLogsCommand(cfCliDefault, cfCliVersionV7, appName);

    assertThat(logsCommand).isNotEmpty();
    assertThat(logsCommand).isEqualTo("cf logs app-name");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetUnmapRouteCommandWithPort() {
    String appName = "app-name";
    String domain = "example.com";
    String port = "8080";
    String unmapRouteCommand =
        CfCliCommandResolver.getUnmapRouteCommand(cfCliPathV6, cfCliVersionV6, appName, domain, port);

    assertThat(unmapRouteCommand).isNotEmpty();
    assertThat(unmapRouteCommand).isEqualTo("/path-to-cf-cli6/cf unmap-route app-name example.com --port 8080");

    unmapRouteCommand = CfCliCommandResolver.getUnmapRouteCommand(cfCliPathV7, cfCliVersionV7, appName, domain, port);

    assertThat(unmapRouteCommand).isNotEmpty();
    assertThat(unmapRouteCommand).isEqualTo("/path-to-cf-cli7/cf7 unmap-route app-name example.com --port 8080");

    unmapRouteCommand = CfCliCommandResolver.getUnmapRouteCommand(cfCliDefault, cfCliVersionV7, appName, domain, port);

    assertThat(unmapRouteCommand).isNotEmpty();
    assertThat(unmapRouteCommand).isEqualTo("cf unmap-route app-name example.com --port 8080");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetUnmapRouteCommand() {
    String appName = "app-name";
    String domain = "example.com";
    String hostname = "my-host";
    String path = "foo";
    String unmapRouteCommand =
        CfCliCommandResolver.getUnmapRouteCommand(cfCliPathV6, cfCliVersionV6, appName, domain, hostname, path);

    assertThat(unmapRouteCommand).isNotEmpty();
    assertThat(unmapRouteCommand)
        .isEqualTo("/path-to-cf-cli6/cf unmap-route app-name example.com --hostname my-host --path foo");

    unmapRouteCommand =
        CfCliCommandResolver.getUnmapRouteCommand(cfCliPathV7, cfCliVersionV7, appName, domain, hostname, path);

    assertThat(unmapRouteCommand).isNotEmpty();
    assertThat(unmapRouteCommand)
        .isEqualTo("/path-to-cf-cli7/cf7 unmap-route app-name example.com --hostname my-host --path foo");

    unmapRouteCommand =
        CfCliCommandResolver.getUnmapRouteCommand(cfCliDefault, cfCliVersionV7, appName, domain, hostname, path);

    assertThat(unmapRouteCommand).isNotEmpty();
    assertThat(unmapRouteCommand).isEqualTo("cf unmap-route app-name example.com --hostname my-host --path foo");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetMapRouteCommandWithPort() {
    String appName = "app-name";
    String domain = "example.com";
    String port = "8080";
    String mapRouteCommand =
        CfCliCommandResolver.getMapRouteCommand(cfCliPathV6, cfCliVersionV6, appName, domain, port);

    assertThat(mapRouteCommand).isNotEmpty();
    assertThat(mapRouteCommand).isEqualTo("/path-to-cf-cli6/cf map-route app-name example.com --port 8080");

    mapRouteCommand = CfCliCommandResolver.getMapRouteCommand(cfCliPathV7, cfCliVersionV7, appName, domain, port);

    assertThat(mapRouteCommand).isNotEmpty();
    assertThat(mapRouteCommand).isEqualTo("/path-to-cf-cli7/cf7 map-route app-name example.com --port 8080");

    mapRouteCommand = CfCliCommandResolver.getMapRouteCommand(cfCliDefault, cfCliVersionV7, appName, domain, port);

    assertThat(mapRouteCommand).isNotEmpty();
    assertThat(mapRouteCommand).isEqualTo("cf map-route app-name example.com --port 8080");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetMapRouteCommand() {
    String appName = "app-name";
    String domain = "example.com";
    String hostname = "my-host";
    String path = "foo";
    String mapRouteCommand =
        CfCliCommandResolver.getMapRouteCommand(cfCliPathV6, cfCliVersionV6, appName, domain, hostname, path);

    assertThat(mapRouteCommand).isNotEmpty();
    assertThat(mapRouteCommand)
        .isEqualTo("/path-to-cf-cli6/cf map-route app-name example.com --hostname my-host --path foo");

    mapRouteCommand =
        CfCliCommandResolver.getMapRouteCommand(cfCliPathV7, cfCliVersionV7, appName, domain, hostname, path);

    assertThat(mapRouteCommand).isNotEmpty();
    assertThat(mapRouteCommand)
        .isEqualTo("/path-to-cf-cli7/cf7 map-route app-name example.com --hostname my-host --path foo");

    mapRouteCommand =
        CfCliCommandResolver.getMapRouteCommand(cfCliDefault, cfCliVersionV7, appName, domain, hostname, path);

    assertThat(mapRouteCommand).isNotEmpty();
    assertThat(mapRouteCommand).isEqualTo("cf map-route app-name example.com --hostname my-host --path foo");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetConfigureAutoscalingCliCommand() {
    String appName = "app-name";
    String autoScalarFilePath = "/path-to-auto-scalar/file";
    String configureAutoscalingCliCommand = CfCliCommandResolver.getConfigureAutoscalingCliCommand(
        cfCliPathV6, cfCliVersionV6, appName, autoScalarFilePath);

    assertThat(configureAutoscalingCliCommand).isNotEmpty();
    assertThat(configureAutoscalingCliCommand)
        .isEqualTo("/path-to-cf-cli6/cf configure-autoscaling app-name /path-to-auto-scalar/file");

    configureAutoscalingCliCommand = CfCliCommandResolver.getConfigureAutoscalingCliCommand(
        cfCliPathV7, cfCliVersionV7, appName, autoScalarFilePath);

    assertThat(configureAutoscalingCliCommand).isNotEmpty();
    assertThat(configureAutoscalingCliCommand)
        .isEqualTo("/path-to-cf-cli7/cf7 configure-autoscaling app-name /path-to-auto-scalar/file");

    configureAutoscalingCliCommand = CfCliCommandResolver.getConfigureAutoscalingCliCommand(
        cfCliDefault, cfCliVersionV7, appName, autoScalarFilePath);

    assertThat(configureAutoscalingCliCommand).isNotEmpty();
    assertThat(configureAutoscalingCliCommand).isEqualTo("cf configure-autoscaling app-name /path-to-auto-scalar/file");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetDisableAutoscalingCliCommand() {
    String appName = "app-name";
    String disableAutoscalingCliCommand =
        CfCliCommandResolver.getDisableAutoscalingCliCommand(cfCliPathV6, cfCliVersionV6, appName);

    assertThat(disableAutoscalingCliCommand).isNotEmpty();
    assertThat(disableAutoscalingCliCommand).isEqualTo("/path-to-cf-cli6/cf disable-autoscaling app-name");

    disableAutoscalingCliCommand =
        CfCliCommandResolver.getDisableAutoscalingCliCommand(cfCliPathV7, cfCliVersionV7, appName);

    assertThat(disableAutoscalingCliCommand).isNotEmpty();
    assertThat(disableAutoscalingCliCommand).isEqualTo("/path-to-cf-cli7/cf7 disable-autoscaling app-name");

    disableAutoscalingCliCommand =
        CfCliCommandResolver.getDisableAutoscalingCliCommand(cfCliDefault, cfCliVersionV7, appName);

    assertThat(disableAutoscalingCliCommand).isNotEmpty();
    assertThat(disableAutoscalingCliCommand).isEqualTo("cf disable-autoscaling app-name");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetEnableAutoscalingCliCommand() {
    String appName = "app-name";
    String enableAutoscalingCliCommand =
        CfCliCommandResolver.getEnableAutoscalingCliCommand(cfCliPathV6, cfCliVersionV6, appName);

    assertThat(enableAutoscalingCliCommand).isNotEmpty();
    assertThat(enableAutoscalingCliCommand).isEqualTo("/path-to-cf-cli6/cf enable-autoscaling app-name");

    enableAutoscalingCliCommand =
        CfCliCommandResolver.getEnableAutoscalingCliCommand(cfCliPathV7, cfCliVersionV7, appName);

    assertThat(enableAutoscalingCliCommand).isNotEmpty();
    assertThat(enableAutoscalingCliCommand).isEqualTo("/path-to-cf-cli7/cf7 enable-autoscaling app-name");

    enableAutoscalingCliCommand =
        CfCliCommandResolver.getEnableAutoscalingCliCommand(cfCliDefault, cfCliVersionV7, appName);

    assertThat(enableAutoscalingCliCommand).isNotEmpty();
    assertThat(enableAutoscalingCliCommand).isEqualTo("cf enable-autoscaling app-name");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetAutoscalingAppsCliCommand() {
    String appName = "app-name";
    String grepCmd = "| grep";
    String autoscalingAppsCliCommand =
        CfCliCommandResolver.getAutoscalingAppsCliCommandWithGrep(cfCliPathV6, cfCliVersionV6, appName);

    assertThat(autoscalingAppsCliCommand).isNotEmpty();
    assertThat(autoscalingAppsCliCommand).isEqualTo("/path-to-cf-cli6/cf autoscaling-apps | grep app-name");

    autoscalingAppsCliCommand =
        CfCliCommandResolver.getAutoscalingAppsCliCommandWithGrep(cfCliPathV7, cfCliVersionV7, appName);

    assertThat(autoscalingAppsCliCommand).isNotEmpty();
    assertThat(autoscalingAppsCliCommand).isEqualTo("/path-to-cf-cli7/cf7 autoscaling-apps | grep app-name");

    autoscalingAppsCliCommand =
        CfCliCommandResolver.getAutoscalingAppsCliCommandWithGrep(cfCliDefault, cfCliVersionV7, appName);

    assertThat(autoscalingAppsCliCommand).isNotEmpty();
    assertThat(autoscalingAppsCliCommand).isEqualTo("cf autoscaling-apps | grep app-name");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPushCliCommand() {
    String pathToManifestFile = "/path-to-manifest-file/file";
    List<String> variableFilePaths =
        Arrays.asList("/path-to-var-file1/file1", "/path-to-var-file2/file2", "/path-to-var-file3/file3");
    String pushCliCommand =
        CfCliCommandResolver.getPushCliCommand(cfCliPathV6, cfCliVersionV6, pathToManifestFile, variableFilePaths);

    assertThat(pushCliCommand).isNotEmpty();
    assertThat(pushCliCommand)
        .isEqualTo(
            "/path-to-cf-cli6/cf push -f /path-to-manifest-file/file --vars-file /path-to-var-file1/file1 --vars-file /path-to-var-file2/file2 --vars-file /path-to-var-file3/file3");

    pushCliCommand =
        CfCliCommandResolver.getPushCliCommand(cfCliPathV7, cfCliVersionV7, pathToManifestFile, variableFilePaths);

    assertThat(pushCliCommand).isNotEmpty();
    assertThat(pushCliCommand)
        .isEqualTo(
            "/path-to-cf-cli7/cf7 push -f /path-to-manifest-file/file --vars-file /path-to-var-file1/file1 --vars-file /path-to-var-file2/file2 --vars-file /path-to-var-file3/file3");

    pushCliCommand =
        CfCliCommandResolver.getPushCliCommand(cfCliDefault, cfCliVersionV7, pathToManifestFile, variableFilePaths);

    assertThat(pushCliCommand).isNotEmpty();
    assertThat(pushCliCommand)
        .isEqualTo(
            "cf push -f /path-to-manifest-file/file --vars-file /path-to-var-file1/file1 --vars-file /path-to-var-file2/file2 --vars-file /path-to-var-file3/file3");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPushCliCommandWithPath() {
    String pathToManifestFile = "/path-to-manifest-file/file";
    String pushCliCommand = CfCliCommandResolver.getPushCliCommand(cfCliPathV6, cfCliVersionV6, pathToManifestFile);

    assertThat(pushCliCommand).isNotEmpty();
    assertThat(pushCliCommand).isEqualTo("/path-to-cf-cli6/cf push -f /path-to-manifest-file/file");

    pushCliCommand = CfCliCommandResolver.getPushCliCommand(cfCliPathV7, cfCliVersionV7, pathToManifestFile);

    assertThat(pushCliCommand).isNotEmpty();
    assertThat(pushCliCommand).isEqualTo("/path-to-cf-cli7/cf7 push -f /path-to-manifest-file/file");

    pushCliCommand = CfCliCommandResolver.getPushCliCommand(cfCliDefault, cfCliVersionV7, pathToManifestFile);

    assertThat(pushCliCommand).isNotEmpty();
    assertThat(pushCliCommand).isEqualTo("cf push -f /path-to-manifest-file/file");
  }
}
