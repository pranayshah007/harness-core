/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.chartmuseum;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.chartmuseum.ChartMuseumConstants.AWS_ACCESS_KEY_ID;
import static io.harness.chartmuseum.ChartMuseumConstants.AWS_SECRET_ACCESS_KEY;
import static io.harness.chartmuseum.ChartMuseumConstants.VERSION;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.version.Version;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.config.K8sGlobalConfigService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.StartedProcess;

@OwnedBy(CDP)
public class ChartMuseumClientHelperTest extends CategoryTest {
  private static final String CHARTMUSEUM_BIN_PATH = "/usr/bin/chartmuseum";

  @Mock private Process process;
  @Mock private StartedProcess startedProcess;
  @Mock private K8sGlobalConfigService k8sGlobalConfigService;

  @InjectMocks @Spy private ChartMuseumClientHelper clientHelper;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    doReturn(CHARTMUSEUM_BIN_PATH).when(k8sGlobalConfigService).getChartMuseumPath(false);
    doReturn(startedProcess).when(clientHelper).startProcess(anyString(), anyMap(), any(StringBuffer.class));
    doReturn(process).when(startedProcess).getProcess();
    doReturn(true).when(process).isAlive();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartServer() throws Exception {
    String chartmuseumCommand = "/usr/bin/chartmuseum --storage=amazon --disable-statefiles";
    Map<String, String> environment = ImmutableMap.of("SECRET_KEY", "secret-key");
    ChartMuseumServer startedServer = clientHelper.startServer(chartmuseumCommand, environment);
    assertThat(startedServer.getStartedProcess()).isEqualTo(startedProcess);

    verify(clientHelper, times(1)).startProcess(eq(chartmuseumCommand), eq(environment), any(StringBuffer.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartServerFailed() {
    doReturn(false).when(process).isAlive();

    assertThatThrownBy(() -> clientHelper.startServer("start server", Collections.emptyMap()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Failed after 5 retries");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getEnvForAwsConfig() {
    testGetEnvForAwsConfig();
    testGetEnvForAwsConfigWithAssumeDelegateRole();
    testGetEnvForAwsConfigWithIRSA();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetVersion() throws Exception {
    final String cliPath = "/usr/local/bin/chartmuseum";
    testGetVersion(cliPath, "ChartMuseum version v0.14.0 (build cc297af)", Version.parse("0.14.0"));
    testGetVersion(cliPath, "ChartMuseum version v0.13.0 (build 3a24659)", Version.parse("0.13.0"));
    testGetVersion(cliPath, "ChartMuseum version 0.8.2 (build 47d5c19", Version.parse("0.8.2"));
  }

  private void testGetEnvForAwsConfigWithIRSA() {
    Map<String, String> env = clientHelper.getEnvForAwsConfig(null, null, false, true);
    assertThat(env.get("AWS_SDK_LOAD_CONFIG").equals(true));
    assertThat(env.get("AWS_ROLE_SESSION_NAME")).contains("aws-sdk-java-");
    assertThat(env.containsKey("AWS_ROLE_ARN"));
    assertThat(env.containsKey("AWS_WEB_IDENTITY_TOKEN_FILE"));
  }

  private void testGetEnvForAwsConfigWithAssumeDelegateRole() {
    Map<String, String> env = clientHelper.getEnvForAwsConfig(null, null, true, false);
    assertThat(env).isEmpty();
  }

  private void testGetEnvForAwsConfig() {
    String accessKey = "access-key";
    String secretKey = "secret-key";
    Map<String, String> env =
        clientHelper.getEnvForAwsConfig(accessKey.toCharArray(), secretKey.toCharArray(), false, false);
    assertThat(env.keySet()).hasSize(2);
    assertThat(env.get(AWS_ACCESS_KEY_ID)).isEqualTo(accessKey);
    assertThat(env.get(AWS_SECRET_ACCESS_KEY)).isEqualTo(secretKey);
  }

  private void testGetVersion(String cliPath, String output, Version expectedVersion) throws Exception {
    doReturn(new ProcessResult(0, new ProcessOutput(output.getBytes(StandardCharsets.UTF_8))))
        .when(clientHelper)
        .executeCommand(cliPath + ' ' + VERSION);

    Version result = clientHelper.getVersion(cliPath);

    assertThat(result).isEqualByComparingTo(expectedVersion);
  }
}
