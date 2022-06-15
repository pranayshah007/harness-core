/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.appservice.webapp.handler.AzureWebAppRequestHandler;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AbstractWebAppTaskRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppTaskRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureWebAppTaskNGTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private AzureWebAppRequestHandler<AbstractWebAppTaskRequest> azureWebAppRequestHandler;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private Map<String, AzureWebAppRequestHandler<? extends AzureWebAppTaskRequest>> requestHandlerMap;
  @Mock private AzureLogCallbackProviderFactory logCallbackProviderFactory;
  @Mock private AzureLogCallbackProvider logCallbackProvider;

  private final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();

  @InjectMocks
  private AzureWebAppTaskNG azureWebAppTaskNG =
      new AzureWebAppTaskNG(delegateTaskPackage, logStreamingTaskClient, response -> {}, () -> true);

  @Before
  public void setup() {
    doReturn(azureWebAppRequestHandler).when(requestHandlerMap).get(AzureWebAppRequestType.SLOT_DEPLOYMENT.name());
    doReturn(logCallbackProvider)
        .when(logCallbackProviderFactory)
        .createNg(eq(logStreamingTaskClient), any(CommandUnitsProgress.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunArrayParameters() {
    assertThatThrownBy(() -> azureWebAppTaskNG.run(new Object[] {null}))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRun() throws Exception {
    final AbstractWebAppTaskRequest abstractWebAppTaskRequest = spy(AbstractWebAppTaskRequest.class);
    final AzureWebAppRequestResponse requestResponse = mock(AzureWebAppRequestResponse.class);

    doReturn(AzureWebAppRequestType.SLOT_DEPLOYMENT).when(abstractWebAppTaskRequest).getRequestType();
    doReturn(requestResponse)
        .when(azureWebAppRequestHandler)
        .handleRequest(abstractWebAppTaskRequest, logCallbackProvider);

    AzureWebAppTaskResponse taskResponse = azureWebAppTaskNG.run(abstractWebAppTaskRequest);
    assertThat(taskResponse.getRequestResponse()).isSameAs(requestResponse);
    assertThat(taskResponse.getCommandUnitsProgress()).isNotNull();

    verify(azureWebAppRequestHandler).handleRequest(abstractWebAppTaskRequest, logCallbackProvider);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunException() {
    final AbstractWebAppTaskRequest abstractWebAppTaskRequest = spy(AbstractWebAppTaskRequest.class);
    final LinkedHashMap<String, CommandUnitProgress> commandUnitsProgressMap = new LinkedHashMap<>();

    commandUnitsProgressMap.put(
        "Fetch Files", CommandUnitProgress.builder().status(CommandExecutionStatus.SUCCESS).build());
    commandUnitsProgressMap.put(
        "Slot Setup", CommandUnitProgress.builder().status(CommandExecutionStatus.SUCCESS).build());

    doReturn(CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitsProgressMap).build())
        .when(abstractWebAppTaskRequest)
        .getCommandUnitsProgress();
    doReturn(AzureWebAppRequestType.SLOT_DEPLOYMENT).when(abstractWebAppTaskRequest).getRequestType();
    doThrow(new RuntimeException("Azure deployment failed"))
        .when(azureWebAppRequestHandler)
        .handleRequest(abstractWebAppTaskRequest, logCallbackProvider);

    assertThatThrownBy(() -> azureWebAppTaskNG.run(abstractWebAppTaskRequest))
        .isInstanceOf(TaskNGDataException.class)
        .matches(exception -> {
          TaskNGDataException ngDataException = (TaskNGDataException) exception;
          assertThat(ngDataException.getCommandUnitsProgress().getUnitProgresses()).hasSize(2);
          return true;
        });
  }
}