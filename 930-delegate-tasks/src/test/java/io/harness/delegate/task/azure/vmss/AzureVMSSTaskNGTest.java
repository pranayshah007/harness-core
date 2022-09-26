/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.vmss;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.azure.vmss.handler.AzureVMSSRequestHandler;
import io.harness.delegate.task.azure.vmss.ng.request.AzureVMSSRequestType;
import io.harness.delegate.task.azure.vmss.ng.request.AzureVMSSTaskRequest;
import io.harness.delegate.task.azure.vmss.ng.response.AzureVMSSRequestResponse;
import io.harness.delegate.task.azure.vmss.ng.response.AzureVMSSTaskRequestResponse;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureVMSSTaskNGTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private SecretDecryptionService decryptionService;
  @Mock private Map<String, AzureVMSSRequestHandler<? extends AzureVMSSTaskRequest>> requestHandlerMap;

  @Mock private AzureVMSSRequestHandler<? extends AzureVMSSTaskRequest> mockRequestHandler;
  @Mock private AzureVMSSRequestResponse mockRequestResponse;
  @Mock private AzureVMSSTaskRequest mockTaskRequest;

  private final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();

  @InjectMocks
  private AzureVMSSTaskNG azureVMSSTaskNG =
      new AzureVMSSTaskNG(delegateTaskPackage, logStreamingTaskClient, response -> {}, () -> true);

  @Before
  public void setup() {
    doReturn(mockRequestHandler).when(requestHandlerMap).get(anyString());
    doReturn(mockRequestResponse).when(mockRequestHandler).handleRequest(any(AzureVMSSTaskRequest.class));
    doReturn(AzureVMSSRequestType.AZURE_VMSS_SETUP).when(mockTaskRequest).getRequestType();
    doReturn(emptyList()).when(mockTaskRequest).fetchDecryptionDetails();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDecryptTaskRequest() throws JoseException, IOException {
    DecryptableEntity mockDecryptableEntity1 = mock(DecryptableEntity.class);
    DecryptableEntity mockDecryptableEntity2 = mock(DecryptableEntity.class);
    List<EncryptedDataDetail> testEncryptedDataDetailList1 =
        Collections.singletonList(EncryptedDataDetail.builder().build());
    List<EncryptedDataDetail> testEncryptedDataDetailList2 =
        asList(EncryptedDataDetail.builder().build(), EncryptedDataDetail.builder().build());

    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> testDecryptionDetails =
        asList(Pair.of(mockDecryptableEntity1, testEncryptedDataDetailList1),
            Pair.of(mockDecryptableEntity1, testEncryptedDataDetailList2),
            Pair.of(mockDecryptableEntity2, testEncryptedDataDetailList2),
            Pair.of(mockDecryptableEntity2, testEncryptedDataDetailList2));

    doReturn(testDecryptionDetails).when(mockTaskRequest).fetchDecryptionDetails();

    azureVMSSTaskNG.run(mockTaskRequest);

    verify(decryptionService, times(1)).decrypt(mockDecryptableEntity1, testEncryptedDataDetailList1);
    verify(decryptionService, times(1)).decrypt(mockDecryptableEntity1, testEncryptedDataDetailList2);
    verify(decryptionService, times(2)).decrypt(mockDecryptableEntity2, testEncryptedDataDetailList2);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMockedRequest() throws JoseException, IOException {
    final CommandUnitsProgress taskCommandUnitProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(new LinkedHashMap<>()).build();
    doReturn(taskCommandUnitProgress).when(mockTaskRequest).getCommandUnitsProgress();

    AzureVMSSTaskRequestResponse response = azureVMSSTaskNG.run(mockTaskRequest);
    assertThat(response.getRequestResponse()).isSameAs(mockRequestResponse);
    assertThat(response.getCommandUnitsProgress())
        .isEqualTo(UnitProgressDataMapper.toUnitProgressData(taskCommandUnitProgress));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMockedRequestFailure() {
    final CommandUnitsProgress taskCommandUnitProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(new LinkedHashMap<>()).build();
    final RuntimeException thrownException = new RuntimeException("Something went wrong");
    doThrow(thrownException).when(mockRequestHandler).handleRequest(mockTaskRequest);
    doReturn(taskCommandUnitProgress).when(mockTaskRequest).getCommandUnitsProgress();

    assertThatThrownBy(() -> azureVMSSTaskNG.run(mockTaskRequest))
        .isInstanceOf(TaskNGDataException.class)
        .hasCause(thrownException)
        .matches(exception -> {
          TaskNGDataException dataException = (TaskNGDataException) exception;
          assertThat(dataException.getCommandUnitsProgress())
              .isEqualTo(UnitProgressDataMapper.toUnitProgressData(taskCommandUnitProgress));
          return true;
        });
  }
}