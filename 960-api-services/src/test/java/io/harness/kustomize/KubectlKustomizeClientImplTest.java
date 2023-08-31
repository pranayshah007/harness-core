/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.kustomize;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.powermock.api.mockito.PowerMockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.cli.CliHelper;
import io.harness.cli.CliResponse;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class KubectlKustomizeClientImplTest extends CategoryTest {
  @Mock private CliHelper cliHelper;
  @Mock LogCallback logCallback;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testBuild() throws InterruptedException, IOException, TimeoutException {
    Kubectl kubectl = Kubectl.client(null, null);
    kubectl.setVersion(Version.parse("1.22"));
    KubectlKustomizeClientImpl kubectlKustomizeClient =
        KubectlKustomizeClientImpl.builder().kubectl(kubectl).cliHelper(cliHelper).build();
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand("kubectl kustomize KUSTOMIZE_DIR_PATH", KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT,
            Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY", logCallback);

    CliResponse actualResponse =
        kubectlKustomizeClient.build("MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testBuildWithKubectlPaths() throws InterruptedException, IOException, TimeoutException {
    Kubectl kubectl = Kubectl.client("kubectlPath", "kubectlKonfigPath");
    kubectl.setVersion(Version.parse("1.22"));
    KubectlKustomizeClientImpl kubectlKustomizeClient =
        KubectlKustomizeClientImpl.builder().kubectl(kubectl).cliHelper(cliHelper).build();
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand("kubectlPath --kubeconfig=kubectlKonfigPath kustomize KUSTOMIZE_DIR_PATH",
            KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY",
            logCallback);

    CliResponse actualResponse =
        kubectlKustomizeClient.build("MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testBuildWithFlags() throws InterruptedException, IOException, TimeoutException {
    Kubectl kubectl = Kubectl.client(null, null);
    kubectl.setVersion(Version.parse("1.22"));
    KubectlKustomizeClientImpl kubectlKustomizeClient = KubectlKustomizeClientImpl.builder()
                                                            .kubectl(kubectl)
                                                            .cliHelper(cliHelper)
                                                            .commandFlags(Map.of("BUILD", "--flag1"))
                                                            .build();
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand("kubectl kustomize KUSTOMIZE_DIR_PATH --flag1", KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT,
            Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY", logCallback);

    CliResponse actualResponse =
        kubectlKustomizeClient.build("MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testBuildWithPluginsAndFlags() throws InterruptedException, IOException, TimeoutException {
    Kubectl kubectl = Kubectl.client(null, null);
    kubectl.setVersion(Version.parse("1.22"));
    KubectlKustomizeClientImpl kubectlKustomizeClient = KubectlKustomizeClientImpl.builder()
                                                            .kubectl(kubectl)
                                                            .cliHelper(cliHelper)
                                                            .commandFlags(Map.of("BUILD", "--flag1"))
                                                            .build();
    // tests correct parameters are passed to executeCliCommand
    CliResponse cliResponse = CliResponse.builder().build();
    doReturn(cliResponse)
        .when(cliHelper)
        .executeCliCommand(
            "XDG_CONFIG_HOME=\"pluginPath\" kubectl kustomize KUSTOMIZE_DIR_PATH --enable-alpha-plugins --flag1",
            KustomizeConstants.KUSTOMIZE_COMMAND_TIMEOUT, Collections.emptyMap(), "MANIFEST_FILES_DIRECTORY",
            logCallback);

    CliResponse actualResponse = kubectlKustomizeClient.buildWithPlugins(
        "MANIFEST_FILES_DIRECTORY", "KUSTOMIZE_DIR_PATH", "pluginPath", logCallback);

    assertThat(actualResponse).isEqualTo(cliResponse);
  }
}