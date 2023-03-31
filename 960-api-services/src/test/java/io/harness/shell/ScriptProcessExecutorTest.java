/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.VED;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ScriptProcessExecutorTest extends CategoryTest {
  String APP_ID = "APP_ID";
  String ACCOUNT_ID = "ACCOUNT_ID";
  String ACTIVITY_ID = "ACTIVITY_ID";

  @Mock private LogCallback logCallback;

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  private ScriptProcessExecutor scriptProcessExecutor;
  private ShellExecutorConfig shellExecutorConfig;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithSweepingOutputOnDelegateSuccess() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .build();

    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    ExecuteCommandResponse executeCommandResponse = scriptProcessExecutor.executeCommandString(
        command, asList("A", "B", "${C}", "${A}"), Collections.emptyList(), null);
    assertThat(executeCommandResponse).isNotNull();
    assertThat(executeCommandResponse.getStatus()).isEqualTo(SUCCESS);
    assertThat(executeCommandResponse.getCommandExecutionData()).isNotNull();
    ShellExecutionData shellExecutionData = (ShellExecutionData) executeCommandResponse.getCommandExecutionData();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).isNotEmpty();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsEntry("A", "aaa");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsEntry("B", "bbb");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsKey("aaa");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).doesNotContainKey("C");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithSweepingOutputOnDelegateFails() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .environment(env)
                              .kubeConfigContent("KubeConfig")
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "exit 1";
    ExecuteCommandResponse executeCommandResponse =
        scriptProcessExecutor.executeCommandString(command, asList("A", "B"));
    assertThat(executeCommandResponse).isNotNull();
    assertThat(executeCommandResponse.getStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(executeCommandResponse.getCommandExecutionData()).isNotNull();
    ShellExecutionData shellExecutionData = (ShellExecutionData) executeCommandResponse.getCommandExecutionData();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).isEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithWorkingDirectoryOnDelegateSuccess() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithoutWorkingDirectoryOnDelegateSuccess() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .environment(env)
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithBadScript() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "efjrls";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(FAILURE);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testGetMethods() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .environment(env)
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
    assertThat(scriptProcessExecutor.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(scriptProcessExecutor.getExecutionId()).isEqualTo(ACTIVITY_ID);
    assertThat(scriptProcessExecutor.getCommandUnitName()).isEqualTo("MyCommand");
    assertThat(scriptProcessExecutor.getHost()).isEqualTo(null);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testExecutePowershellScriptWithoutWorkingDirectoryOnDelegateSuccess() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.POWERSHELL)
                              .environment(env)
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testExecutePowershellScriptWithWorkingDirectoryOnDelegateSuccess() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.POWERSHELL)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testWithKubeConfigContent() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.POWERSHELL)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .kubeConfigContent("KubeConfig")
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    CommandExecutionStatus commandExecutionStatus = scriptProcessExecutor.executeCommandString(command, null, true);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithKubeConfig() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .kubeConfigContent("KubeConfig")
                              .build();

    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    ExecuteCommandResponse executeCommandResponse = scriptProcessExecutor.executeCommandString(
        command, asList("A", "B", "${C}", "${A}"), Collections.emptyList(), null);
    assertThat(executeCommandResponse).isNotNull();
    assertThat(executeCommandResponse.getStatus()).isEqualTo(SUCCESS);
    assertThat(executeCommandResponse.getCommandExecutionData()).isNotNull();
    ShellExecutionData shellExecutionData = (ShellExecutionData) executeCommandResponse.getCommandExecutionData();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).isNotEmpty();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsEntry("A", "aaa");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsEntry("B", "bbb");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsKey("aaa");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).doesNotContainKey("C");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testExecuteBashScriptWithInvalidVariables() {
    Map<String, String> env = new HashMap<>();
    env.put("PATH", "/Users/user/bin:/usr/local/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin");
    shellExecutorConfig = ShellExecutorConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .appId(APP_ID)
                              .commandUnitName("MyCommand")
                              .executionId(ACTIVITY_ID)
                              .scriptType(ScriptType.BASH)
                              .workingDirectory("/tmp")
                              .environment(env)
                              .kubeConfigContent("KubeConfig")
                              .build();

    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "export A=\"aaa\"\n"
        + "export B=\"bbb\"";
    ExecuteCommandResponse executeCommandResponse = scriptProcessExecutor.executeCommandString(
        command, asList("A", "B", "${C}", "${A}", "hyphen-var", "nonExistentVar"), Collections.emptyList(), null);
    assertThat(executeCommandResponse).isNotNull();
    assertThat(executeCommandResponse.getStatus()).isEqualTo(SUCCESS);
    assertThat(executeCommandResponse.getCommandExecutionData()).isNotNull();
    ShellExecutionData shellExecutionData = (ShellExecutionData) executeCommandResponse.getCommandExecutionData();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).isNotEmpty();

    // 2 warnings for hyphenated and empty variables
    verify(logCallback, times(2)).saveExecutionLog(any(), eq(LogLevel.WARN), any());
  }
}
