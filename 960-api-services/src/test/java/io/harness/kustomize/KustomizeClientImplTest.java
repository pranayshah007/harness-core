/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

public class KustomizeClientImplTest extends CategoryTest {
  @Mock private CliHelper cliHelper;
  @Mock LogCallback logCallback;
  @InjectMocks private KustomizeClientFactory kustomizeClientFactory;
  @InjectMocks private KustomizeClientImpl kustomizeClientImpl = getKustomizeClient("");
  @InjectMocks private KustomizeClientImpl kustomizeClientV3 = getKustomizeClient("3.5.4");
  @InjectMocks private KustomizeClientImpl kustomizeClientV4_0_1 = getKustomizeClient("4.0.1");
  private static final String KUSTOMIZE_VERSION_COMMAND = "KUSTOMIZE_BINARY_PATH version";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testBuild() throws InterruptedException, IOException, TimeoutException {
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand("KUSTOMIZE_BINARY_PATH build  KUSTOMIZE_DIR_PATH",
            io.harness.kustomize.KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(),
            "MANIFEST_FILES_DIRECTORY", logCallback);

    CliResponse actualResponse =
        kustomizeClientImpl.build("MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testBuildWithPluginsOldFlag() throws Exception {
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    testGetVersion(KUSTOMIZE_VERSION_COMMAND,
        "{Version:kustomize/v3.5.4 GitCommit:a414f75f1b6bd01f888bb99360c69a4221116bf8 BuildDate:2021-02-12T22:53:04Z GoOs:linux GoArch:amd64}",
        Version.parse("3.5.4"));

    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(
            "XDG_CONFIG_HOME=PLUGIN_PATH KUSTOMIZE_BINARY_PATH build --enable_alpha_plugins  KUSTOMIZE_DIR_PATH",
            KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY",
            logCallback);

    CliResponse actualResponse = kustomizeClientV3.buildWithPlugins(
        "MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", "PLUGIN_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testBuildWithPluginsLatestKustomize() throws Exception {
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    testGetVersion(KUSTOMIZE_VERSION_COMMAND,
        "{Version:kustomize/v4.0.1 GitCommit:a414f75f1b6bd01f888bb99360c69a4221116bf8 BuildDate:2021-02-12T22:53:04Z GoOs:linux GoArch:amd64}",
        Version.parse("4.0.1"));

    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(
            "XDG_CONFIG_HOME=PLUGIN_PATH KUSTOMIZE_BINARY_PATH build --enable-alpha-plugins  KUSTOMIZE_DIR_PATH",
            KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY",
            logCallback);

    CliResponse actualResponse = kustomizeClientV4_0_1.buildWithPlugins(
        "MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", "PLUGIN_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testBuildWithPluginsNoKustomizeVersion() throws Exception {
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    testGetVersion(KUSTOMIZE_VERSION_COMMAND,
        "{Version:kustomize GitCommit:a414f75f1b6bd01f888bb99360c69a4221116bf8 BuildDate:2021-02-12T22:53:04Z GoOs:linux GoArch:amd64}",
        Version.parse("0.0.1"));

    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(
            "XDG_CONFIG_HOME=PLUGIN_PATH KUSTOMIZE_BINARY_PATH build --enable_alpha_plugins  KUSTOMIZE_DIR_PATH",
            KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY",
            logCallback);

    CliResponse actualResponse = kustomizeClientImpl.buildWithPlugins(
        "MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", "PLUGIN_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  private void testGetVersion(String command, String output, Version expectedVersion) throws Exception {
    Mockito.doReturn(new ProcessResult(0, new ProcessOutput(output.getBytes(StandardCharsets.UTF_8))))
        .when(cliHelper)
        .executeCommand(command);

    Version result = kustomizeClientFactory.getVersion(command);

    assertThat(result).isEqualByComparingTo(expectedVersion);
  }

  private KustomizeClientImpl getKustomizeClient(String version) {
    if (isEmpty(version)) {
      version = "0.0.1";
    }
    return KustomizeClientImpl.builder()
        .kustomizeBinaryPath("KUSTOMIZE_BINARY_PATH")
        .version(Version.parse(version))
        .build();
  }
}
