/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.shellscript.provisioner;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import software.wings.WingsBaseTest;
import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.intfc.security.EncryptionService;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ShellScriptProvisionTaskTest extends WingsBaseTest {
  @Mock private EncryptionService encryptionService;
  @Mock private ShellExecutorFactory shellExecutorFactory;
  @Mock private DelegateLogService logService;
  @Mock private DelegateFileManager delegateFileManager;

  @InjectMocks
  private ShellScriptProvisionTask shellScriptProvisionTask = new ShellScriptProvisionTask(
      DelegateTaskPackage.builder().delegateId("delegateid").data(TaskData.builder().build()).build(), null,
      notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testGetCombinedVariablesMap() throws IOException {
    assertThat(shellScriptProvisionTask.getCombinedVariablesMap(null, null)).isEmpty();
    assertThat(shellScriptProvisionTask.getCombinedVariablesMap(Collections.emptyMap(), Collections.emptyMap()))
        .isEmpty();

    Map<String, String> textVariables = new HashMap<>();
    textVariables.put("var1", "val1");
    assertThat(shellScriptProvisionTask.getCombinedVariablesMap(textVariables, Collections.emptyMap()))
        .isEqualTo(textVariables);

    Map<String, EncryptedDataDetail> encryptedVariables = new HashMap<>();
    encryptedVariables.put("var2", EncryptedDataDetail.builder().build());

    when(encryptionService.getDecryptedValue(any(), eq(false))).thenReturn(new char[] {'a', 'b'});
    Map<String, String> expectedCombinedMap = new HashMap<>();
    expectedCombinedMap.put("var1", "val1");
    expectedCombinedMap.put("var2", "ab");

    assertThat(shellScriptProvisionTask.getCombinedVariablesMap(textVariables, encryptedVariables))
        .isEqualTo(expectedCombinedMap);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldUseWorkflowExecutionIdAsOutputPath() {
    ShellScriptProvisionParameters taskParameters = ShellScriptProvisionParameters.builder()
                                                        .accountId("account-id")
                                                        .workflowExecutionId("workflow-execution-id")
                                                        .build();
    ArgumentCaptor<ShellExecutorConfig> shellExecutorConfigArgumentCaptor =
        ArgumentCaptor.forClass(ShellExecutorConfig.class);

    shellScriptProvisionTask.run(taskParameters);

    verify(shellExecutorFactory).getExecutor(shellExecutorConfigArgumentCaptor.capture());
    assertThat(shellExecutorConfigArgumentCaptor.getValue().getEnvironment().values())
        .anyMatch(item -> item.endsWith("workflow-execution-id/output.json"));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecutionFailure() {
    String scriptBody = "scriptBody";
    String errorMessage = "errorMessage";
    ShellScriptProvisionParameters taskParameters = ShellScriptProvisionParameters.builder()
                                                        .workflowExecutionId("workflow-execution-id")
                                                        .scriptBody(scriptBody)
                                                        .build();

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build();

    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    when(scriptProcessExecutor.executeCommandString(anyString(), anyList(), anyList(), any()))
        .thenReturn(executeCommandResponse);
    when(shellExecutorFactory.getExecutor(any(ShellExecutorConfig.class))).thenReturn(scriptProcessExecutor);

    ShellScriptProvisionExecutionData shellScriptProvisionExecutionData = shellScriptProvisionTask.run(taskParameters);
    assertThat(shellScriptProvisionExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    verify(scriptProcessExecutor, times(1))
        .executeCommandString(eq(scriptBody), eq(emptyList()), eq(emptyList()), eq(null));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecutionSuccess() {
    String scriptBody = "echo \"Message\" > $PROVISIONER_OUTPUT_PATH";
    ShellScriptProvisionParameters taskParameters = ShellScriptProvisionParameters.builder()
                                                        .workflowExecutionId("workflow-execution-id")
                                                        .scriptBody(scriptBody)
                                                        .accountId(ACCOUNT_ID)
                                                        .appId(APP_ID)
                                                        .activityId(ACTIVITY_ID)
                                                        .outputPathKey("PROVISIONER_OUTPUT_PATH")
                                                        .build();

    ShellExecutorFactory shellExecutorFactory = new ShellExecutorFactory();
    on(shellScriptProvisionTask).set("shellExecutorFactory", shellExecutorFactory);

    on(shellExecutorFactory).set("logService", logService);
    on(shellExecutorFactory).set("fileService", delegateFileManager);

    ShellScriptProvisionExecutionData shellScriptProvisionExecutionData = shellScriptProvisionTask.run(taskParameters);
    assertThat(shellScriptProvisionExecutionData.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(shellScriptProvisionExecutionData.getOutput().trim()).isEqualTo("Message");
  }
}
