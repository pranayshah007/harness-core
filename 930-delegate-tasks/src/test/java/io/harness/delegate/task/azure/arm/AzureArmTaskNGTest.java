/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.handlers.AzureResourceCreationAbstractTaskHandler;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;
import io.harness.exception.UnexpectedTypeException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class AzureArmTaskNGTest extends CategoryTest {
  @Spy private Map<AzureARMTaskType, AzureResourceCreationAbstractTaskHandler> handlerMap = new HashMap<>();
  @Mock private AzureResourceCreationAbstractTaskHandler handler;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;

  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private AzureLogCallbackProviderFactory logCallbackProviderFactory;

  @Mock private ExecutorService executorService;

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private LogCallback mockLogCallback;

  @InjectMocks
  AzureResourceCreationTaskNG azureARMTaskNG =
      new AzureResourceCreationTaskNG(DelegateTaskPackage.builder().data(TaskData.builder().build()).build(),
          logStreamingTaskClient, mock(Consumer.class), mock(BooleanSupplier.class));

  AzureResourceCreationTaskNGParameters taskNGParameters;
  private Future<?> future;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    doReturn(mockLogStreamingTaskClient).when(logCallbackProviderFactory).createNg(any(), any());
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());

    AzureConnectorDTO connectorDTO = AzureConnectorDTO.builder()
                                         .credential(AzureCredentialDTO.builder()
                                                         .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                                                         .config(AzureInheritFromDelegateDetailsDTO.builder()
                                                                     .authDTO(AzureMSIAuthUADTO.builder().build())
                                                                     .build())
                                                         .build())
                                         .build();

    taskNGParameters = AzureARMTaskNGParameters.builder()
                           .accountId("accountId")
                           .taskType(AzureARMTaskType.ARM_DEPLOYMENT)
                           .templateBody(AppSettingsFile.builder().fileContent("templateBody").build())
                           .parametersBody(AppSettingsFile.builder().fileContent("parameters").build())
                           .connectorDTO(connectorDTO)
                           .scopeType(ARMScopeType.RESOURCE_GROUP)
                           .subscriptionId("subscriptionId")
                           .resourceGroupName("resourceGroupName")
                           .deploymentMode(AzureDeploymentMode.COMPLETE)
                           .timeoutInMs(100000)
                           .encryptedDataDetails(Collections.singletonList(EncryptedDataDetail.builder().build()))
                           .deploymentDataLocation("eastus")
                           .build();

    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doReturn(future).when(executorService).submit(any(Runnable.class));
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    handlerMap.put(AzureARMTaskType.ARM_DEPLOYMENT, handler);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAzureTaskNGWithParams() throws Exception {
    doReturn(AzureARMTaskNGResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(handler)
        .executeTask(eq(taskNGParameters), any(), any(), any());

    AzureResourceCreationTaskNGResponse response =
        (AzureResourceCreationTaskNGResponse) azureARMTaskNG.run(taskNGParameters);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAzureTaskNGWithParamsAndThrowsException() throws Exception {
    doThrow(new RuntimeException("Test Error Message"))
        .when(handler)
        .executeTask(eq(taskNGParameters), any(), any(), any());

    assertThatThrownBy(() -> azureARMTaskNG.run(taskNGParameters)).isInstanceOf(TaskNGDataException.class);
  }

  @Test(expected = UnexpectedTypeException.class)
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testAzureTaskNGWithParamsAndNoTaskType() throws Exception {
    handlerMap.remove(AzureARMTaskType.ARM_DEPLOYMENT);
    azureARMTaskNG.run(taskNGParameters);
  }
}
