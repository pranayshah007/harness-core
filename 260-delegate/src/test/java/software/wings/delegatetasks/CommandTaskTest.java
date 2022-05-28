/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.SAHIL;

import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;
import static software.wings.beans.artifact.ArtifactFile.Builder.anArtifactFile;
import static software.wings.beans.command.CommandExecutionContext.Builder.aCommandExecutionContext;
import static software.wings.beans.command.CommandType.ENABLE;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.FILE_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.PUBLIC_DNS;
import static software.wings.utils.WingsTestConstants.SSH_USER_NAME;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.CommandExecutionContext;
import software.wings.beans.dto.Command;
import software.wings.beans.infrastructure.Host;
import software.wings.service.intfc.ServiceCommandExecutorService;
import software.wings.service.intfc.security.SSHVaultService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CommandTaskTest extends WingsBaseTest {
  @Mock ServiceCommandExecutorService serviceCommandExecutorService;
  @Mock private SSHVaultService sshVaultService;

  DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder()
          .delegateId("delegateid")
          .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
          .build();

  @InjectMocks CommandTask commandTask = new CommandTask(delegateTaskPackage, null, null, null);

  private Host.Builder builder = aHost().withAppId(APP_ID).withHostName(HOST_NAME).withPublicDns(PUBLIC_DNS);
  private CommandExecutionContext commandExecutionContextBuider =
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
          .accountId(ACCOUNT_ID)
          .build();

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRunWithObjectParameters() {
    Command command = Command.builder().name("Ami-Command").commandType(ENABLE).build();
    CommandExecutionResult expectedCommandExecutionResult =
        CommandExecutionResult.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .errorMessage(null)
            .commandExecutionData(commandExecutionContextBuider.getCommandExecutionData())
            .build();
    when(serviceCommandExecutorService.execute(command, commandExecutionContextBuider))
        .thenReturn(CommandExecutionStatus.SUCCESS);
    CommandExecutionResult commandExecutionResult =
        commandTask.run(new Object[] {command, commandExecutionContextBuider});
    assertEquals(expectedCommandExecutionResult, commandExecutionResult);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRunWithObjectParametersException() {
    Command command = Command.builder().name("Ami-Command").commandType(ENABLE).build();
    CommandExecutionResult expectedCommandExecutionResult =
        CommandExecutionResult.builder()
            .status(CommandExecutionStatus.FAILURE)
            .errorMessage("NullPointerException")
            .commandExecutionData(commandExecutionContextBuider.getCommandExecutionData())
            .build();
    when(serviceCommandExecutorService.execute(command, commandExecutionContextBuider))
        .thenThrow(new NullPointerException());
    CommandExecutionResult commandExecutionResult =
        commandTask.run(new Object[] {command, commandExecutionContextBuider});
    assertEquals(expectedCommandExecutionResult, commandExecutionResult);
  }
}
