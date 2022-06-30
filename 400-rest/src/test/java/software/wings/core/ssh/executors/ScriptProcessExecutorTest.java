/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.filesystem.FileIo.deleteFileIfExists;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.AADITI;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;

import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionData;
import io.harness.shell.ShellExecutorConfig;

import software.wings.WingsBaseTest;
import software.wings.delegatetasks.DelegateFileManager;

import com.google.common.io.CharStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

public class ScriptProcessExecutorTest extends WingsBaseTest {
  @Mock private DelegateFileManager delegateFileManager;
  @Mock private LogCallback logCallback;

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  private ScriptProcessExecutor scriptProcessExecutor;
  private FileBasedProcessScriptExecutor fileBasedProcessScriptExecutor;
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
    fileBasedProcessScriptExecutor =
        new FileBasedProcessScriptExecutor(delegateFileManager, logCallback, true, shellExecutorConfig);
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
                              .build();
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
    on(scriptProcessExecutor).set("logCallback", logCallback);

    String command = "exit 1";
    ExecuteCommandResponse executeCommandResponse =
        scriptProcessExecutor.executeCommandString(command, asList("A", "B"), Collections.emptyList(), null);
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
  public void testScpOneFileSuccess() throws IOException {
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, ShellExecutorConfig.builder().build());
    fileBasedProcessScriptExecutor = new FileBasedProcessScriptExecutor(
        delegateFileManager, logCallback, true, ShellExecutorConfig.builder().build());
    on(scriptProcessExecutor).set("logCallback", logCallback);

    File file = testFolder.newFile();
    CharStreams.asWriter(new FileWriter(file)).append("ANY_TEXT").close();

    AbstractScriptExecutor.FileProvider fileProvider = new AbstractScriptExecutor.FileProvider() {
      @Override
      public Pair<String, Long> getInfo() throws IOException {
        File file1 = new File(file.getAbsolutePath());
        return ImmutablePair.of(file1.getName(), file1.length());
      }

      @Override
      public void downloadToStream(OutputStream outputStream) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
          IOUtils.copy(fis, outputStream);
        }
      }
    };
    CommandExecutionStatus commandExecutionStatus = fileBasedProcessScriptExecutor.scpOneFile("/tmp", fileProvider);
    assertThat(commandExecutionStatus).isEqualTo(SUCCESS);
    File tempFile = new File("/tmp/" + fileProvider.getInfo().getKey());
    boolean exists = tempFile.exists();
    assertThat(exists).isTrue();

    // cleanup
    deleteFileIfExists(tempFile.getAbsolutePath());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testScpOneFileFails() {
    scriptProcessExecutor = new ScriptProcessExecutor(logCallback, true, ShellExecutorConfig.builder().build());
    fileBasedProcessScriptExecutor = new FileBasedProcessScriptExecutor(
        delegateFileManager, logCallback, true, ShellExecutorConfig.builder().build());
    on(scriptProcessExecutor).set("logCallback", logCallback);

    AbstractScriptExecutor.FileProvider fileProvider = new AbstractScriptExecutor.FileProvider() {
      @Override
      public Pair<String, Long> getInfo() throws IOException {
        return ImmutablePair.of(null, 0L);
      }

      @Override
      public void downloadToStream(OutputStream outputStream) throws IOException {
        try (FileInputStream fis = new FileInputStream("")) {
          IOUtils.copy(fis, outputStream);
        }
      }
    };
    CommandExecutionStatus commandExecutionStatus =
        fileBasedProcessScriptExecutor.scpOneFile("/randomdir", fileProvider);
    assertThat(commandExecutionStatus).isEqualTo(FAILURE);
  }
}
