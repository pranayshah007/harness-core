/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.FileBucket.ARTIFACTS;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.shell.AccessType.USER_PASSWORD;
import static io.harness.shell.AuthenticationScheme.SSH_KEY;
import static io.harness.shell.ExecutorType.BASTION_HOST;
import static io.harness.shell.ExecutorType.KEY_AUTH;
import static io.harness.shell.ExecutorType.PASSWORD_AUTH;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static software.wings.beans.BastionConnectionAttributes.Builder.aBastionConnectionAttributes;
import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.artifact.ArtifactFile.Builder.anArtifactFile;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.beans.command.ExecCommandUnit.Builder.anExecCommandUnit;
import static software.wings.beans.command.ScpCommandUnit.Builder.aScpCommandUnit;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.BASTION_CONN_ATTR_ID;
import static software.wings.utils.WingsTestConstants.FILE_ID;
import static software.wings.utils.WingsTestConstants.FILE_PATH;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_ID;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_KEY_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.PUBLIC_DNS;
import static software.wings.utils.WingsTestConstants.SSH_USER_NAME;
import static software.wings.utils.WingsTestConstants.SSH_USER_PASSWORD;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.shell.AccessType;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;

import software.wings.WingsBaseTest;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitHelper;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.InitSshCommandUnit;
import software.wings.beans.command.InitSshCommandUnitV2;
import software.wings.beans.command.ScpCommandUnit;
import software.wings.beans.command.ScpCommandUnit.ScpFileCategory;
import software.wings.beans.dto.Command;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.Host.Builder;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.core.ssh.executors.FileBasedSshScriptExecutor;
import software.wings.core.ssh.executors.SshExecutorFactory;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.SshCommandUnitExecutorServiceImpl;
import software.wings.service.intfc.CommandUnitExecutorService;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Injector;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by anubhaw on 5/31/16.
 */
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class SshCommandUnitExecutorServiceTest extends WingsBaseTest {
  /**
   * The constant EXEC_CMD.
   */
  public static final String EXEC_CMD = "ls";
  private static final SettingAttribute HOST_CONN_ATTR_PWD = aSettingAttribute()
                                                                 .withUuid(HOST_CONN_ATTR_ID)
                                                                 .withValue(aHostConnectionAttributes()
                                                                                .withAccessType(USER_PASSWORD)
                                                                                .withUserName(SSH_USER_NAME)
                                                                                .withSshPassword(SSH_USER_PASSWORD)
                                                                                .build())
                                                                 .build();
  private static final SettingAttribute BASTION_HOST_ATTR =
      aSettingAttribute()
          .withUuid(BASTION_CONN_ATTR_ID)
          .withValue(aBastionConnectionAttributes().withHostName(HOST_NAME).withKey(WingsTestConstants.SSH_KEY).build())
          .build();
  private static final SettingAttribute HOST_CONN_ATTR_KEY = aSettingAttribute()
                                                                 .withUuid(HOST_CONN_ATTR_KEY_ID)
                                                                 .withValue(aHostConnectionAttributes()
                                                                                .withAccessType(AccessType.KEY)
                                                                                .withKey(WingsTestConstants.SSH_KEY)
                                                                                .withUserName(SSH_USER_NAME)
                                                                                .build())
                                                                 .build();

  private static final ExecCommandUnit EXEC_COMMAND_UNIT = anExecCommandUnit().withCommandString(EXEC_CMD).build();
  /**
   * The Ssh executor factory.
   */
  @Mock private SshExecutorFactory sshExecutorFactory;
  @Mock private ScriptSshExecutor scriptSshExecutor;
  @Mock private FileBasedSshScriptExecutor fileBasedSshScriptExecutor;
  @Mock private ScriptProcessExecutor scriptProcessExecutor;
  @Mock private ShellExecutorFactory shellExecutorFactory;

  @Mock private Injector injector;
  /**
   * The Log service.
   */
  @Mock private DelegateLogService logService;

  @InjectMocks
  private CommandUnitExecutorService sshCommandUnitExecutorService = new SshCommandUnitExecutorServiceImpl();

  private Builder builder = aHost().withAppId(APP_ID).withHostName(HOST_NAME).withPublicDns(PUBLIC_DNS);
  private CommandExecutionContext.Builder commandExecutionContextBuider =
      aCommandExecutionContext()
          .appId(APP_ID)
          .activityId(ACTIVITY_ID)
          .runtimePath("/tmp/runtime")
          .backupPath("/tmp/backup")
          .stagingPath("/tmp/staging")
          .executionCredential(aSSHExecutionCredential().withSshUser(SSH_USER_NAME).build())
          .artifactFiles(Lists.newArrayList(anArtifactFile().withName("artifact.war").withFileUuid(FILE_ID).build()))
          .serviceVariables(ImmutableMap.of("PORT", "8080", "PASSWORD", "aSecret"))
          .safeDisplayServiceVariables(ImmutableMap.of("PORT", "8080", "PASSWORD", "*****"))
          .host(builder.build())
          .accountId(ACCOUNT_ID);

  @Before
  public void setupMocks() {
    on(sshCommandUnitExecutorService).set("timeLimiter", new FakeTimeLimiter());
  }

  /**
   * Should create password based ssh config.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCreatePasswordBasedSshConfig() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    SshSessionConfig expectedSshConfig = aSshSessionConfig()
                                             .withAppId(APP_ID)
                                             .withExecutionId(ACTIVITY_ID)
                                             .withHost(PUBLIC_DNS)
                                             .withUserName(SSH_USER_NAME)
                                             .withExecutorType(PASSWORD_AUTH)
                                             .withSshPassword(SSH_USER_PASSWORD)
                                             .withAccountId(ACCOUNT_ID)
                                             .withAccessType(USER_PASSWORD)
                                             .withAuthenticationScheme(SSH_KEY)
                                             .build();

    when(sshExecutorFactory.getExecutor(expectedSshConfig)).thenReturn(scriptSshExecutor);
    sshCommandUnitExecutorService.execute(EXEC_COMMAND_UNIT,
        commandExecutionContextBuider.but().hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO()).build());
    verify(sshExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * Should create password based ssh config and executeOnDelegate as true
   */
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void shouldCreatePasswordBasedSshConfigAndExecuteOnDelegate() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    ShellExecutorConfig expectedSshConfig = ShellExecutorConfig.builder()
                                                .appId(APP_ID)
                                                .executionId(ACTIVITY_ID)
                                                .accountId(ACCOUNT_ID)
                                                .environment(new HashMap<>())
                                                .build();

    when(shellExecutorFactory.getExecutor(expectedSshConfig)).thenReturn(scriptProcessExecutor);
    sshCommandUnitExecutorService.execute(EXEC_COMMAND_UNIT,
        commandExecutionContextBuider.but()
            .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
            .executeOnDelegate(true)
            .build());
    verify(shellExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * test execute when it throws TimeOutException
   */
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExecuteTimeoutException() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    ShellExecutorConfig expectedSshConfig = ShellExecutorConfig.builder()
                                                .appId(APP_ID)
                                                .executionId(ACTIVITY_ID)
                                                .accountId(ACCOUNT_ID)
                                                .environment(new HashMap<>())
                                                .build();
    CommandExecutionContext commandExecutionContext = commandExecutionContextBuider.but()
                                                          .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
                                                          .executeOnDelegate(true)
                                                          .build();
    CommandUnit commandUnit = mock(CommandUnit.class);
    when(commandUnit.execute(any())).thenThrow(new UncheckedTimeoutException());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> sshCommandUnitExecutorService.execute(commandUnit, commandExecutionContext))
        .withMessage("SOCKET_CONNECTION_TIMEOUT");
    verify(shellExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * test execute when it throws WingsException
   */
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExecuteWingsException() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    ShellExecutorConfig expectedSshConfig = ShellExecutorConfig.builder()
                                                .appId(APP_ID)
                                                .executionId(ACTIVITY_ID)
                                                .accountId(ACCOUNT_ID)
                                                .environment(new HashMap<>())
                                                .build();
    CommandExecutionContext commandExecutionContext = commandExecutionContextBuider.but()
                                                          .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
                                                          .executeOnDelegate(true)
                                                          .build();
    CommandUnit commandUnit = mock(CommandUnit.class);
    when(commandUnit.execute(any()))
        .thenThrow(new WingsException(ErrorCode.INVALID_KEY, "Test error", WingsException.USER_SRE));
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> sshCommandUnitExecutorService.execute(commandUnit, commandExecutionContext))
        .withMessage("Test error");
    verify(shellExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * test execute when it throws WingsException
   */
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExecuteWingsExceptionWithoutMessageList() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    ShellExecutorConfig expectedSshConfig = ShellExecutorConfig.builder()
                                                .appId(APP_ID)
                                                .executionId(ACTIVITY_ID)
                                                .accountId(ACCOUNT_ID)
                                                .environment(new HashMap<>())
                                                .build();
    CommandExecutionContext commandExecutionContext = commandExecutionContextBuider.but()
                                                          .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
                                                          .executeOnDelegate(true)
                                                          .build();
    CommandUnit commandUnit = mock(CommandUnit.class);
    when(commandUnit.execute(any()))
        .thenThrow(new WingsException(ErrorCode.INVALID_KEY, "Test error", WingsException.SRE));
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> sshCommandUnitExecutorService.execute(commandUnit, commandExecutionContext))
        .withMessage("Test error");
    verify(shellExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * test execute when it throws NullPointerException
   */
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExecuteWingsNullPointerException() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    ShellExecutorConfig expectedSshConfig = ShellExecutorConfig.builder()
                                                .appId(APP_ID)
                                                .executionId(ACTIVITY_ID)
                                                .accountId(ACCOUNT_ID)
                                                .environment(new HashMap<>())
                                                .build();
    CommandExecutionContext commandExecutionContext = commandExecutionContextBuider.but()
                                                          .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
                                                          .executeOnDelegate(true)
                                                          .build();
    CommandUnit commandUnit = mock(CommandUnit.class);
    when(commandUnit.execute(any())).thenThrow(new NullPointerException());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> sshCommandUnitExecutorService.execute(commandUnit, commandExecutionContext))
        .withMessage("UNKNOWN_ERROR");
    verify(shellExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * Should create key based ssh config.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCreateKeyBasedSshConfig() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_KEY.getUuid()).build();
    SshSessionConfig expectedSshConfig = aSshSessionConfig()
                                             .withAppId(APP_ID)
                                             .withExecutionId(ACTIVITY_ID)
                                             .withHost(PUBLIC_DNS)
                                             .withUserName(SSH_USER_NAME)
                                             .withExecutorType(KEY_AUTH)
                                             .withKey(WingsTestConstants.SSH_KEY)
                                             .withKeyName(HOST_CONN_ATTR_KEY.getUuid())
                                             .withAccountId(ACCOUNT_ID)
                                             .build();

    when(sshExecutorFactory.getExecutor(expectedSshConfig)).thenReturn(scriptSshExecutor);
    sshCommandUnitExecutorService.execute(EXEC_COMMAND_UNIT,
        commandExecutionContextBuider.but().hostConnectionAttributes(HOST_CONN_ATTR_KEY.toDTO()).build());
    verify(sshExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * Should create bastion host based ssh config.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldCreateBastionHostBasedSshConfig() {
    SshSessionConfig expectedSshConfig = aSshSessionConfig()
                                             .withAppId(APP_ID)
                                             .withExecutionId(ACTIVITY_ID)
                                             .withHost(PUBLIC_DNS)
                                             .withUserName(SSH_USER_NAME)
                                             .withExecutorType(BASTION_HOST)
                                             .withAuthenticationScheme(SSH_KEY)
                                             .withAccessType(USER_PASSWORD)
                                             .withSshPassword(SSH_USER_PASSWORD)
                                             .withBastionHostConfig(aSshSessionConfig()
                                                                        .withHost(HOST_NAME)
                                                                        .withKey(WingsTestConstants.SSH_KEY)
                                                                        .withKeyName(BASTION_HOST_ATTR.getUuid())
                                                                        .build())
                                             .withAccountId(ACCOUNT_ID)
                                             .build();

    when(sshExecutorFactory.getExecutor(expectedSshConfig)).thenReturn(scriptSshExecutor);
    sshCommandUnitExecutorService.execute(EXEC_COMMAND_UNIT,
        commandExecutionContextBuider.but()
            .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
            .bastionConnectionAttributes(BASTION_HOST_ATTR.toDTO())
            .build());
    verify(sshExecutorFactory).getExecutor(expectedSshConfig);
  }

  /**
   * Should execute exec command.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldExecuteExecCommand() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    sshCommandUnitExecutorService.execute(EXEC_COMMAND_UNIT,
        commandExecutionContextBuider.but().hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO()).build());
    verify(scriptSshExecutor).executeCommandString(EXEC_COMMAND_UNIT.getPreparedCommand(), false);
  }

  /**
   * Should execute copy command.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldExecuteCopyCommand() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    ScpCommandUnit commandUnit = aScpCommandUnit()
                                     .withCommandUnitType(CommandUnitType.SCP)
                                     .withDestinationDirectoryPath(FILE_PATH)
                                     .withFileCategory(ScpFileCategory.ARTIFACTS)
                                     .build();

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(sshExecutorFactory.getFileBasedExecutor(any(SshSessionConfig.class))).thenReturn(fileBasedSshScriptExecutor);
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().metadataOnly(false).build();
    sshCommandUnitExecutorService.execute(commandUnit,
        commandExecutionContextBuider.but()
            .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
            .artifactStreamAttributes(artifactStreamAttributes)
            .build());
    verify(fileBasedSshScriptExecutor)
        .copyGridFsFiles(commandUnit.getDestinationDirectoryPath(), ARTIFACTS,
            Lists.newArrayList(org.apache.commons.lang3.tuple.Pair.of(FILE_ID, null)));
  }

  /**
   * Should execute init command.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldExecuteInitCommand() throws IOException {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    InitSshCommandUnit commandUnit = new InitSshCommandUnit();
    on(commandUnit).set("commandUnitHelper", new CommandUnitHelper());
    Command command =
        Command.builder()
            .commandUnits(asList(commandUnit,
                anExecCommandUnit().withName("dols").withCommandPath("/tmp").withCommandString("ls").build()))
            .build();
    commandUnit.setCommand(command);

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(sshExecutorFactory.getFileBasedExecutor(any(SshSessionConfig.class))).thenReturn(fileBasedSshScriptExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(fileBasedSshScriptExecutor.copyFiles(anyString(), anyList())).thenReturn(CommandExecutionStatus.SUCCESS);

    sshCommandUnitExecutorService.execute(
        commandUnit, commandExecutionContextBuider.but().hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO()).build());
    verify(scriptSshExecutor).executeCommandString("mkdir -p /tmp/ACTIVITY_ID", false);

    String actualLauncherScript =
        new File(System.getProperty("java.io.tmpdir"), "harnesslauncherACTIVITY_ID.sh").getAbsolutePath();
    verify(fileBasedSshScriptExecutor).copyFiles("/tmp/ACTIVITY_ID", asList(actualLauncherScript));
    assertThat(new File(actualLauncherScript)).doesNotExist();

    String expectedExecCommandUnitScript =
        new File(System.getProperty("java.io.tmpdir"), "harness" + DigestUtils.md5Hex("dolsACTIVITY_ID"))
            .getAbsolutePath();
    verify(fileBasedSshScriptExecutor).copyFiles("/tmp/ACTIVITY_ID", asList(expectedExecCommandUnitScript));
    verify(scriptSshExecutor).executeCommandString("chmod 0700 /tmp/ACTIVITY_ID/*", false);

    assertThat(new File(expectedExecCommandUnitScript)).doesNotExist();
    assertThat((ExecCommandUnit) command.getCommandUnits().get(1))
        .extracting(ExecCommandUnit::getPreparedCommand)
        .isEqualTo(
            "/tmp/ACTIVITY_ID/harnesslauncherACTIVITY_ID.sh -w '/tmp' harness" + DigestUtils.md5Hex("dolsACTIVITY_ID"));
  }

  /**
   * Should execute init command.
   *
   */
  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteInitCommandV2() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    InitSshCommandUnitV2 commandUnit = new InitSshCommandUnitV2();
    on(commandUnit).set("commandUnitHelper", new CommandUnitHelper());
    Command command =
        Command.builder()
            .commandUnits(asList(commandUnit,
                anExecCommandUnit().withName("dols").withCommandPath("/tmp").withCommandString("ls").build()))
            .build();
    commandUnit.setCommand(command);

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);

    sshCommandUnitExecutorService.execute(commandUnit,
        commandExecutionContextBuider.but()
            .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
            .inlineSshCommand(true)
            .build());
    verify(scriptSshExecutor).executeCommandString("mkdir -p /tmp/ACTIVITY_ID", false);
    assertThat(((ExecCommandUnit) command.getCommandUnits().get(1)).getPreparedCommand()).contains("ls");
  }

  /**
   * Should execute init command with nested units.
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldExecuteInitCommandWithNestedUnits() throws IOException {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    InitSshCommandUnit commandUnit = new InitSshCommandUnit();
    on(commandUnit).set("commandUnitHelper", new CommandUnitHelper());
    Command command =
        Command.builder()
            .commandUnits(asList(commandUnit,
                anExecCommandUnit().withName("dols").withCommandPath("/tmp").withCommandString("ls").build(),
                Command.builder()
                    .name("start1")
                    .commandUnits(asList(anExecCommandUnit()
                                             .withName("startscript")
                                             .withCommandString("start.sh")
                                             .withCommandPath("/home/tomcat")
                                             .build()))
                    .build()))
            .build();
    commandUnit.setCommand(command);

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(sshExecutorFactory.getFileBasedExecutor(any(SshSessionConfig.class))).thenReturn(fileBasedSshScriptExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(fileBasedSshScriptExecutor.copyFiles(anyString(), anyList())).thenReturn(CommandExecutionStatus.SUCCESS);
    sshCommandUnitExecutorService.execute(
        commandUnit, commandExecutionContextBuider.but().hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO()).build());

    String expectedExecCommandUnitScript =
        new File(System.getProperty("java.io.tmpdir"), "harness" + DigestUtils.md5Hex("dolsACTIVITY_ID"))
            .getAbsolutePath();
    String expectedSubExecCommandUnitScript =
        new File(System.getProperty("java.io.tmpdir"), "harness" + DigestUtils.md5Hex("start1startscriptACTIVITY_ID"))
            .getAbsolutePath();

    verify(fileBasedSshScriptExecutor)
        .copyFiles("/tmp/ACTIVITY_ID", asList(expectedExecCommandUnitScript, expectedSubExecCommandUnitScript));

    assertThat(new File(expectedExecCommandUnitScript)).doesNotExist();
    assertThat((ExecCommandUnit) command.getCommandUnits().get(1))
        .extracting(ExecCommandUnit::getPreparedCommand)
        .isEqualTo(
            "/tmp/ACTIVITY_ID/harnesslauncherACTIVITY_ID.sh -w '/tmp' harness" + DigestUtils.md5Hex("dolsACTIVITY_ID"));

    assertThat(new File(expectedSubExecCommandUnitScript)).doesNotExist();
    assertThat((ExecCommandUnit) ((Command) command.getCommandUnits().get(2)).getCommandUnits().get(0))
        .extracting(ExecCommandUnit::getPreparedCommand)
        .isEqualTo("/tmp/ACTIVITY_ID/harnesslauncherACTIVITY_ID.sh -w '/home/tomcat' harness"
            + DigestUtils.md5Hex("start1startscriptACTIVITY_ID"));

    verify(scriptSshExecutor).executeCommandString("chmod 0700 /tmp/ACTIVITY_ID/*", false);
  }

  /**
   * Should execute init command with nested units.
   *
   */
  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteInitCommandV2WithNestedUnits() {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    InitSshCommandUnitV2 commandUnit = new InitSshCommandUnitV2();
    on(commandUnit).set("commandUnitHelper", new CommandUnitHelper());
    Command command =
        Command.builder()
            .commandUnits(asList(commandUnit,
                anExecCommandUnit().withName("dols").withCommandPath("/tmp").withCommandString("ls").build(),
                Command.builder()
                    .name("start1")
                    .commandUnits(asList(anExecCommandUnit()
                                             .withName("startscript")
                                             .withCommandString("start.sh")
                                             .withCommandPath("/home/tomcat")
                                             .build()))
                    .build()))
            .build();
    commandUnit.setCommand(command);

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);
    sshCommandUnitExecutorService.execute(commandUnit,
        commandExecutionContextBuider.but()
            .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
            .inlineSshCommand(true)
            .build());

    assertThat(((ExecCommandUnit) command.getCommandUnits().get(1)).getPreparedCommand()).contains("ls");
    assertThat(
        ((ExecCommandUnit) ((Command) command.getCommandUnits().get(2)).getCommandUnits().get(0)).getPreparedCommand())
        .contains("start.sh");

    verify(scriptSshExecutor).executeCommandString("mkdir -p /tmp/ACTIVITY_ID", false);
  }

  /**
   * Should execute init command without unresolved env variables
   *
   */
  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldExecuteInitCommandWithoutUnresolvedEnvironmentVariables() throws IOException {
    // this path name should not be added as Env Variable
    String badSubstitutionPathName = "/test/${service.name}/${env.name}/backup";

    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();
    InitSshCommandUnit commandUnit = new InitSshCommandUnit();
    on(commandUnit).set("commandUnitHelper", new CommandUnitHelper());
    Command command =
        Command.builder()
            .commandUnits(asList(commandUnit,
                anExecCommandUnit().withName("dols").withCommandPath("/tmp").withCommandString("ls").build()))
            .build();
    commandUnit.setCommand(command);

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(sshExecutorFactory.getFileBasedExecutor(any(SshSessionConfig.class))).thenReturn(fileBasedSshScriptExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(fileBasedSshScriptExecutor.copyFiles(anyString(), anyList())).thenReturn(CommandExecutionStatus.SUCCESS);

    sshCommandUnitExecutorService.execute(commandUnit,
        commandExecutionContextBuider.but()
            .stagingPath(badSubstitutionPathName)
            .runtimePath(badSubstitutionPathName)
            .backupPath(badSubstitutionPathName)
            .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
            .build());

    assertThat(commandUnit.getExecutionStagingDir()).isEqualTo("/tmp/ACTIVITY_ID");
    assertThat(commandUnit.fetchEnvVariables().get("WINGS_RUNTIME_PATH")).isEqualTo(null);
    assertThat(commandUnit.fetchEnvVariables().get("WINGS_STAGING_PATH")).isEqualTo(null);
    assertThat(commandUnit.fetchEnvVariables().get("WINGS_BACKUP_PATH")).isEqualTo(null);
  }

  /**
   * Should execute init command with resolved env variables
   *
   * @throws IOException the io exception
   */
  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldExecuteInitCommandWithResolvedEnvironmentVariables() throws IOException {
    Host host = builder.withHostConnAttr(HOST_CONN_ATTR_PWD.getUuid()).build();

    InitSshCommandUnit commandUnit = new InitSshCommandUnit();
    on(commandUnit).set("commandUnitHelper", new CommandUnitHelper());
    Command command =
        Command.builder()
            .commandUnits(asList(commandUnit,
                anExecCommandUnit().withName("dols").withCommandPath("/tmp").withCommandString("ls").build()))
            .build();
    commandUnit.setCommand(command);

    when(sshExecutorFactory.getExecutor(any(SshSessionConfig.class))).thenReturn(scriptSshExecutor);
    when(sshExecutorFactory.getFileBasedExecutor(any(SshSessionConfig.class))).thenReturn(fileBasedSshScriptExecutor);
    when(scriptSshExecutor.executeCommandString(anyString(), anyBoolean())).thenReturn(CommandExecutionStatus.SUCCESS);
    when(fileBasedSshScriptExecutor.copyFiles(anyString(), anyList())).thenReturn(CommandExecutionStatus.SUCCESS);

    sshCommandUnitExecutorService.execute(
        commandUnit, commandExecutionContextBuider.but().hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO()).build());

    assertThat(commandUnit.getExecutionStagingDir()).isEqualTo("/tmp/ACTIVITY_ID");
    assertThat(commandUnit.fetchEnvVariables().get("WINGS_RUNTIME_PATH")).isEqualTo("/tmp/runtime");
    assertThat(commandUnit.fetchEnvVariables().get("WINGS_STAGING_PATH")).isEqualTo("/tmp/staging");
    assertThat(commandUnit.fetchEnvVariables().get("WINGS_BACKUP_PATH")).isEqualTo("/tmp/backup");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testExecuteWithSocketTimeoutException() {
    ShellExecutorConfig expectedSshConfig = ShellExecutorConfig.builder()
                                                .appId(APP_ID)
                                                .executionId(ACTIVITY_ID)
                                                .accountId(ACCOUNT_ID)
                                                .environment(new HashMap<>())
                                                .build();
    CommandExecutionContext commandExecutionContext = commandExecutionContextBuider.but()
                                                          .hostConnectionAttributes(HOST_CONN_ATTR_PWD.toDTO())
                                                          .executeOnDelegate(true)
                                                          .build();
    CommandUnit commandUnit = mock(CommandUnit.class);

    when(commandUnit.execute(any())).thenThrow(new UncheckedTimeoutException());
    assertThatExceptionOfType(WingsException.class)
        .isThrownBy(() -> sshCommandUnitExecutorService.execute(commandUnit, commandExecutionContext))
        .withMessage("SOCKET_CONNECTION_TIMEOUT");
    verify(shellExecutorFactory).getExecutor(expectedSshConfig);
  }
}
