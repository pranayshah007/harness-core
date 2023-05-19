/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice;

import static io.harness.azure.model.AzureConstants.COMMAND_TYPE_BLANK_VALIDATION_MSG;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType.SLOT_SETUP;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.delegatetasks.azure.AzureSecretHelper;
import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppSlotSetupTaskHandler;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAppServiceTaskTest extends CategoryTest {
  @Mock private AzureSecretHelper azureSecretHelper;
  @Spy AzureWebAppSlotSetupTaskHandler setupTaskHandler = new AzureWebAppSlotSetupTaskHandler();
  private final AzureAppServiceTaskFactory azureAppServiceTaskFactory = new AzureAppServiceTaskFactory();

  @InjectMocks
  private final AzureAppServiceTask azureAppServiceTask =
      new AzureAppServiceTask(DelegateTaskPackage.builder()
                                  .delegateId("delegateId")
                                  .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())

                                  .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    Map<String, AbstractAzureAppServiceTaskHandler> azureAppServiceTaskTypeToTaskHandlerMap = new HashMap<>();
    azureAppServiceTaskTypeToTaskHandlerMap.put(SLOT_SETUP.name(), setupTaskHandler);
    doReturn(AzureTaskExecutionResponse.builder().build())
        .when(setupTaskHandler)
        .executeTask(any(), any(), any(), any());

    on(azureAppServiceTaskFactory)
        .set("azureAppServiceTaskTypeToTaskHandlerMap", azureAppServiceTaskTypeToTaskHandlerMap);
    on(azureAppServiceTask).set("azureAppServiceTaskFactory", azureAppServiceTaskFactory);
    on(azureAppServiceTask).set("azureSecretHelper", azureSecretHelper);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRun() {
    AzureWebAppSlotSetupParameters taskParameters = AzureWebAppSlotSetupParameters.builder().build();
    AzureTaskExecutionRequest request = AzureTaskExecutionRequest.builder().azureTaskParameters(taskParameters).build();

    AzureTaskExecutionResponse response = azureAppServiceTask.run(request);

    assertThat(response).isNotNull();
    verify(azureSecretHelper).decryptAzureAppServiceTaskParameters(eq(taskParameters));
    verify(azureSecretHelper).encryptAzureTaskResponseParams(any(), any(), any());

    assertThatThrownBy(() -> azureAppServiceTaskFactory.getAzureAppServiceTask(""))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(COMMAND_TYPE_BLANK_VALIDATION_MSG);
  }

  @Test(expected = NotImplementedException.class)
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRunNotImplemented() {
    Object[] parameters = new Object[2];
    azureAppServiceTask.run(parameters);
  }

  @Test()
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRunInvalidRequest() {
    AzureTaskExecutionResponse response = azureAppServiceTask.run(AzureVMSSCommandRequest.builder().build());
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
