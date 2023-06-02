/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.VED;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@RunWith(MockitoJUnitRunner.class)
public class ScriptSshExecutorTest extends CategoryTest {
  private static final String ENV_VAR_VALUE = "/some/valid/path";

  @Mock private SshSessionConfig sshSessionConfig;

  private ScriptSshExecutor scriptSshExecutor;

  @Mock private LogCallback logCallback;

  String APP_ID = "APP_ID";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String ACTIVITY_ID = "ACTIVITY_ID";
  Map<String, String> ENV_VARS = Map.ofEntries(Map.entry("Path", ENV_VAR_VALUE), Map.entry("HOME", ENV_VAR_VALUE));

  @Before
  public void setup() throws Exception {
    when(sshSessionConfig.getExecutionId()).thenReturn("ID");
    scriptSshExecutor = spy(new ScriptSshExecutor(logCallback, true, sshSessionConfig));
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void shouldRecognizeEnvVarsInPathAndReplaceWithValuesExtractedFromSystem() throws Exception {
    new EnvironmentVariables(ENV_VARS).execute(() -> {
      assertThat(scriptSshExecutor.resolveEnvVarsInPath("$HOME")).isEqualTo(ENV_VAR_VALUE);
      assertThat(scriptSshExecutor.resolveEnvVarsInPath("$HOME/abc/$Path"))
          .isEqualTo(ENV_VAR_VALUE + "/abc" + ENV_VAR_VALUE);
      assertThat(scriptSshExecutor.resolveEnvVarsInPath("$HOME/work$Path/abc"))
          .isEqualTo(ENV_VAR_VALUE + "/work" + ENV_VAR_VALUE + "/abc");
    });
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void test1() {
    sshSessionConfig.setAccountId(ACCOUNT_ID);
    sshSessionConfig.setAppId(APP_ID);
    sshSessionConfig.setExecutionId(ACTIVITY_ID);
    sshSessionConfig.setWorkingDirectory("/tmp");
    sshSessionConfig.setCommandUnitName("MyCommandUnit");

    ExecuteCommandResponse executeCommandResponse = ExecuteCommandResponse.builder().status(SUCCESS).build();

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";

    doReturn(executeCommandResponse)
        .when(scriptSshExecutor)
        .getExecuteCommandResponse(command, new ArrayList<>(), new ArrayList<>(), false);

    ExecuteCommandResponse response = scriptSshExecutor.executeCommandString(command, new ArrayList<>());

    assertThat(response.getStatus()).isEqualTo(executeCommandResponse.getStatus());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetMethods() {
    SshSessionConfig config = new SshSessionConfig();
    config.setAccountId(ACCOUNT_ID);
    config.setAppId(APP_ID);
    config.setExecutionId(ACTIVITY_ID);
    config.setWorkingDirectory("/tmp");
    config.setCommandUnitName("MyCommandUnit");
    config.setHost("host");

    ScriptSshExecutor executor = new ScriptSshExecutor(logCallback, false, config);

    assertThat(executor.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(executor.getExecutionId()).isEqualTo(ACTIVITY_ID);
    assertThat(executor.getHost()).isEqualTo("host");
    assertThat(executor.getCommandUnitName()).isEqualTo("MyCommandUnit");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testBuildingEnvironmentVars() {
    Map<String, String> envVars = new HashMap<>();
    envVars.put("var", "value");
    String e1 = scriptSshExecutor.buildExportForEnvironmentVariables(envVars);
    assertThat(e1).isEqualTo("export var=\"value\"\n");

    envVars.put("var", "spaced value");
    e1 = scriptSshExecutor.buildExportForEnvironmentVariables(envVars);
    assertThat(e1).isEqualTo("export var=\"spaced value\"\n");

    envVars.put("var", "\"#1$010");
    e1 = scriptSshExecutor.buildExportForEnvironmentVariables(envVars);
    assertThat(e1).isEqualTo("export var=\"\"#1$010\"\n");
  }
}
