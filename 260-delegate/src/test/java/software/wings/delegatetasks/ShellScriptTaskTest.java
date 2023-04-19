/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.winrm.AuthenticationScheme.NTLM;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.shell.AccessType.KEY;
import static io.harness.shell.ScriptType.BASH;
import static io.harness.shell.ScriptType.POWERSHELL;

import static software.wings.beans.ConnectionType.SSH;
import static software.wings.beans.ConnectionType.WINRM;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.DOMAIN;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.winrm.DefaultWinRmExecutor;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.shell.AuthenticationScheme;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.ShellExecutionData;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;

import software.wings.WingsBaseTest;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.core.ssh.executors.SshExecutorFactory;
import software.wings.core.winrm.executors.WinRmExecutorFactory;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.service.intfc.security.EncryptionService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellScriptTaskTest extends WingsBaseTest {
  @Mock ShellExecutorFactory shellExecutorFactory;
  @Mock SshExecutorFactory sshExecutorFactory;
  @Mock WinRmExecutorFactory winrmExecutorFactory;
  @Mock ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Mock EncryptionService encryptionService;
  @Mock ScriptSshExecutor scriptSshExecutor;
  @Mock ScriptProcessExecutor scriptProcessExecutor;
  @Mock DefaultWinRmExecutor defaultWinRmExecutor;
  @Mock DelegateLogService logService;
  @Mock ShellExecutorConfig shellExecutorConfig;
  @Mock ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @InjectMocks ShellScriptTaskHandler shellScriptTaskHandler;

  EncryptedDataDetail encryptedDataDetail1 = EncryptedDataDetail.builder()
                                                 .encryptedData(EncryptedRecordData.builder().build())
                                                 .fieldName("sshPassword")
                                                 .build();
  EncryptedDataDetail encryptedDataDetail2 =
      EncryptedDataDetail.builder().encryptedData(EncryptedRecordData.builder().build()).fieldName("key").build();
  EncryptedDataDetail encryptedDataDetail3 = EncryptedDataDetail.builder()
                                                 .encryptedData(EncryptedRecordData.builder().build())
                                                 .fieldName("passphrase")
                                                 .build();
  EncryptedDataDetail encryptedDataDetail4 = EncryptedDataDetail.builder()
                                                 .encryptedData(EncryptedRecordData.builder().build())
                                                 .fieldName("kerberosPassword")
                                                 .build();

  @InjectMocks
  private ShellScriptTask shellScriptTask =
      new ShellScriptTask(DelegateTaskPackage.builder()
                              .delegateId("delid1")

                              .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())

                              .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(shellScriptTaskHandler).set("shellExecutorFactory", shellExecutorFactory);
    on(shellScriptTaskHandler).set("containerDeploymentDelegateHelper", containerDeploymentDelegateHelper);
    on(shellScriptTaskHandler).set("encryptionService", encryptionService);
    on(shellScriptTaskHandler).set("sshExecutorFactory", sshExecutorFactory);
    on(shellScriptTask).set("shellScriptTaskHandler", shellScriptTaskHandler);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteBashScriptSuccessfullyOnDelegate() {
    ArgumentCaptor<ShellExecutorConfig> shellExecutorConfigArgumentCaptor =
        ArgumentCaptor.forClass(ShellExecutorConfig.class);
    when(shellExecutorFactory.getExecutor(any(ShellExecutorConfig.class), eq(true))).thenReturn(scriptProcessExecutor);
    Map<String, String> map = new HashMap<>();
    map.put("A", "aaa");
    map.put("B", "bbb");
    when(scriptProcessExecutor.executeCommandString(anyString(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder()
                        .status(CommandExecutionStatus.SUCCESS)
                        .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(map).build())
                        .build());

    ShellScriptParameters params = ShellScriptParameters.builder()
                                       .accountId(ACCOUNT_ID)
                                       .appId(APP_ID)
                                       .activityId(ACTIVITY_ID)
                                       .executeOnDelegate(true)
                                       .connectionType(SSH)
                                       .scriptType(BASH)
                                       .workingDirectory("/tmp")
                                       .script("export A=\"aaa\"\n"
                                           + "export B=\"bbb\"")
                                       .outputVars("A,B")
                                       .saveExecutionLogs(true)
                                       .build();

    CommandExecutionResult commandExecutionResult = shellScriptTask.run(params);
    assertThat(commandExecutionResult).isNotNull();
    assertThat(commandExecutionResult.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(commandExecutionResult.getCommandExecutionData()).isNotNull();
    ShellExecutionData shellExecutionData = (ShellExecutionData) commandExecutionResult.getCommandExecutionData();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).isNotEmpty();
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsEntry("A", "aaa");
    assertThat(shellExecutionData.getSweepingOutputEnvVariables()).containsEntry("B", "bbb");
    verify(shellExecutorFactory).getExecutor(shellExecutorConfigArgumentCaptor.capture(), eq(true));
    ShellExecutorConfig shellExecutorConfig = shellExecutorConfigArgumentCaptor.getValue();
    assertThat(shellExecutorConfig).isNotNull();
    assertThat(shellExecutorConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(shellExecutorConfig.getAppId()).isEqualTo(APP_ID);
    assertThat(shellExecutorConfig.getExecutionId()).isEqualTo(ACTIVITY_ID);
    assertThat(shellExecutorConfig.getCommandUnitName()).isEqualTo("Execute");
    assertThat(shellExecutorConfig.getWorkingDirectory()).isEqualTo("/tmp");
    assertThat(shellExecutorConfig.getScriptType()).isEqualTo(BASH);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldFailBashScriptOnDelegate() {
    ShellScriptParameters params = ShellScriptParameters.builder()
                                       .accountId(ACCOUNT_ID)
                                       .appId(APP_ID)
                                       .activityId(ACTIVITY_ID)
                                       .executeOnDelegate(true)
                                       .connectionType(SSH)
                                       .scriptType(BASH)
                                       .script("exit 1")
                                       .outputVars("A,B")
                                       .saveExecutionLogs(true)
                                       .build();
    when(shellExecutorFactory.getExecutor(any(), eq(true))).thenReturn(scriptProcessExecutor);
    when(scriptProcessExecutor.executeCommandString(anyString(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build());
    CommandExecutionResult commandExecutionResult = shellScriptTask.run(params);
    assertThat(commandExecutionResult).isNotNull();
    assertThat(commandExecutionResult.getStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldRunPowershellScriptOnDelegate() {
    ShellScriptParameters params = ShellScriptParameters.builder()
                                       .accountId(ACCOUNT_ID)
                                       .appId(APP_ID)
                                       .activityId(ACTIVITY_ID)
                                       .workingDirectory("/tmp")
                                       .scriptType(POWERSHELL)
                                       .script("Write-Host hello")
                                       .connectionType(SSH)
                                       .executeOnDelegate(true)
                                       .saveExecutionLogs(true)
                                       .build();

    ArgumentCaptor<String> scriptStringCaptor = ArgumentCaptor.forClass(String.class);
    when(shellExecutorFactory.getExecutor(any(ShellExecutorConfig.class), eq(true))).thenReturn(scriptProcessExecutor);
    when(scriptProcessExecutor.executeCommandString(scriptStringCaptor.capture(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder()
                        .status(CommandExecutionStatus.SUCCESS)
                        .commandExecutionData(ShellExecutionData.builder().build())
                        .build());

    CommandExecutionResult commandExecutionResult = shellScriptTask.run(params);

    assertThat(commandExecutionResult).isNotNull();
    assertThat(commandExecutionResult.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(commandExecutionResult.getCommandExecutionData()).isNotNull();
    assertThat(scriptStringCaptor.getValue()).isEqualTo("Write-Host hello");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteBashOnTargetHostSuccess() {
    ShellScriptParameters params =
        ShellScriptParameters.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .activityId(ACTIVITY_ID)
            .host("host")
            .userName(USER_NAME)
            .connectionType(SSH)
            .keyEncryptedDataDetails(asList(encryptedDataDetail2))
            .workingDirectory("/tmp")
            .scriptType(BASH)
            .script("exit 1")
            .outputVars("A,B")
            .hostConnectionAttributes(HostConnectionAttributes.Builder.aHostConnectionAttributes()
                                          .withAccountId(ACCOUNT_ID)
                                          .withConnectionType(HostConnectionAttributes.ConnectionType.SSH)
                                          .withAccessType(KEY)
                                          .withUserName(USER_NAME)
                                          .withEncryptedSshPassword("ENCR")
                                          .withSshPort(22)
                                          .withEncryptedKey("enc")
                                          .withEncryptedPassphrase("")
                                          .withAuthenticationScheme(AuthenticationScheme.SSH_KEY)
                                          .build())
            .port(22)
            .accessType(KEY)
            .authenticationScheme(AuthenticationScheme.SSH_KEY)
            .keyName("KEY_NAME")
            .saveExecutionLogs(true)
            .build();

    ArgumentCaptor<SshSessionConfig> sshSessionConfigArgumentCaptor = ArgumentCaptor.forClass(SshSessionConfig.class);
    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class), eq(true))).thenReturn(scriptSshExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    CommandExecutionResult commandExecutionResult = shellScriptTask.run(params);
    assertThat(commandExecutionResult).isNotNull();
    assertThat(commandExecutionResult.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(sshExecutorFactory).getExecutor(sshSessionConfigArgumentCaptor.capture(), eq(true));
    SshSessionConfig sshSessionConfig = sshSessionConfigArgumentCaptor.getValue();
    assertThat(sshSessionConfig).isNotNull();
    assertThat(sshSessionConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(sshSessionConfig.getAppId()).isEqualTo(APP_ID);
    assertThat(sshSessionConfig.getExecutionId()).isEqualTo(ACTIVITY_ID);
    assertThat(sshSessionConfig.getCommandUnitName()).isEqualTo("Execute");
    assertThat(sshSessionConfig.getHost()).isEqualTo("host");
    assertThat(sshSessionConfig.getAuthenticationScheme()).isEqualTo(AuthenticationScheme.SSH_KEY);
    assertThat(sshSessionConfig.getKeyName()).isEqualTo("KEY_NAME");
    assertThat(sshSessionConfig.getAccessType()).isEqualTo(KEY);
    assertThat(sshSessionConfig.getUserName()).isEqualTo(USER_NAME);
    assertThat(sshSessionConfig.getWorkingDirectory()).isEqualTo("/tmp");
    assertThat(sshSessionConfig.getKey()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecutePowershellScriptOnTargetHostSuccess() {
    ShellScriptParameters params = ShellScriptParameters.builder()
                                       .accountId(ACCOUNT_ID)
                                       .appId(APP_ID)
                                       .activityId(ACTIVITY_ID)
                                       .host("host")
                                       .userName(USER_NAME)
                                       .connectionType(WINRM)
                                       .keyEncryptedDataDetails(asList(encryptedDataDetail2))
                                       .workingDirectory("/tmp")
                                       .scriptType(POWERSHELL)
                                       .script("exit 1")
                                       .outputVars("A,B")
                                       .winrmConnectionAttributes(WinRmConnectionAttributes.builder()
                                                                      .accountId(ACCOUNT_ID)
                                                                      .authenticationScheme(NTLM)
                                                                      .username(USER_NAME)
                                                                      .password(PASSWORD)
                                                                      .domain(DOMAIN)
                                                                      .port(22)
                                                                      .useSSL(true)
                                                                      .skipCertChecks(true)
                                                                      .build())
                                       .workingDirectory("%TEMP%")
                                       .environment(Collections.EMPTY_MAP)
                                       .saveExecutionLogs(true)
                                       .build();
    ArgumentCaptor<WinRmSessionConfig> winRmSessionConfigArgumentCaptor =
        ArgumentCaptor.forClass(WinRmSessionConfig.class);
    when(winrmExecutorFactory.getExecutor(any(WinRmSessionConfig.class), anyBoolean(), eq(true), eq(false)))
        .thenReturn(defaultWinRmExecutor);
    when(defaultWinRmExecutor.executeCommandString(anyString(), anyList(), anyList(), any()))
        .thenReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build());
    CommandExecutionResult commandExecutionResult = shellScriptTask.run(params);
    assertThat(commandExecutionResult).isNotNull();
    assertThat(commandExecutionResult.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(winrmExecutorFactory)
        .getExecutor(winRmSessionConfigArgumentCaptor.capture(), anyBoolean(), eq(true), eq(false));
    WinRmSessionConfig winRmSessionConfig = winRmSessionConfigArgumentCaptor.getValue();
    assertThat(winRmSessionConfig).isNotNull();
    assertThat(winRmSessionConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(winRmSessionConfig.getAppId()).isEqualTo(APP_ID);
    assertThat(winRmSessionConfig.getExecutionId()).isEqualTo(ACTIVITY_ID);
    assertThat(winRmSessionConfig.getCommandUnitName()).isEqualTo("Execute");
    assertThat(winRmSessionConfig.getHostname()).isEqualTo("host");
    assertThat(winRmSessionConfig.getAuthenticationScheme()).isEqualTo(NTLM);
    assertThat(winRmSessionConfig.getDomain()).isEqualTo(DOMAIN);
    assertThat(winRmSessionConfig.getUsername()).isEqualTo(USER_NAME);
    assertThat(winRmSessionConfig.getPassword()).isEqualTo(String.valueOf(PASSWORD));
    assertThat(winRmSessionConfig.isUseSSL()).isTrue();
    assertThat(winRmSessionConfig.isSkipCertChecks()).isTrue();
    assertThat(winRmSessionConfig.getWorkingDirectory()).isEqualTo("%TEMP%");
  }
}
