/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.cv;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.delegatetasks.DelegateCVActivityLogService;
import software.wings.delegatetasks.DelegateCVActivityLogService.Logger;
import software.wings.delegatetasks.DelegateCVTaskService;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.DataCollectionTaskResult.DataCollectionTaskStatus;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AbstractDataCollectionTaskTest extends CategoryTest {
  @Mock private DataCollectorFactory dataCollectorFactory;
  @Mock private Injector injector;
  @Mock EncryptionService encryptionService;
  @Mock private DelegateCVActivityLogService delegateCVActivityLogService;
  @Mock private Logger logger;
  @Mock private DelegateLogService delegateLogService;
  @Mock private DelegateCVTaskService cvTaskService;
  @Mock private RequestExecutor requestExecutor;
  private AbstractDataCollectionTask<DataCollectionInfoV2> abstractDataCollectionTask;

  @Before
  public void setupTests() throws IllegalAccessException, DataCollectionException {
    initMocks(this);
    abstractDataCollectionTask = mock(AbstractDataCollectionTask.class, Mockito.CALLS_REAL_METHODS);
    when(delegateCVActivityLogService.getLogger(any(), any(), anyLong(), any(), any(), anyLong(), anyLong()))
        .thenReturn(logger);
    FieldUtils.writeField(abstractDataCollectionTask, "dataCollectorFactory", dataCollectorFactory, true);
    FieldUtils.writeField(abstractDataCollectionTask, "injector", injector, true);
    FieldUtils.writeField(abstractDataCollectionTask, "cvActivityLogService", delegateCVActivityLogService, true);
    FieldUtils.writeField(abstractDataCollectionTask, "encryptionService", encryptionService, true);
    FieldUtils.writeField(abstractDataCollectionTask, "delegateLogService", delegateLogService, true);
    FieldUtils.writeField(abstractDataCollectionTask, "cvTaskService", cvTaskService, true);
    FieldUtils.writeField(abstractDataCollectionTask, "requestExecutor", requestExecutor, true);
    when(requestExecutor.executeRequest(any(), any(), any())).thenReturn(mock(Object.class));
    CVConstants.RETRY_SLEEP_DURATION = Duration.ofMillis(1); // to run retry based test faster.
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCallToInitAndCollectAndSaveDataWithCorrectParams()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             DataCollectionException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    abstractDataCollectionTask.run(dataCollectionInfo);
    verify(dataCollector, times(1)).init(any(), eq(dataCollectionInfo));
    verify(abstractDataCollectionTask, times(1)).collectAndSaveData(dataCollectionInfo);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDecryptionOfSettingsIfEncryptableSettingIsPresent() {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    EncryptableSetting encryptableSetting = mock(EncryptableSetting.class);
    List<EncryptedDataDetail> encryptedDataDetails = mock(List.class);
    when(dataCollectionInfo.getEncryptableSetting()).thenReturn(Optional.of(encryptableSetting));
    when(dataCollectionInfo.getEncryptedDataDetails()).thenReturn(encryptedDataDetails);
    abstractDataCollectionTask.run(dataCollectionInfo);
    verify(encryptionService, times(1)).decrypt(eq(encryptableSetting), eq(encryptedDataDetails), eq(false));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDecryptionIfEncryptableSettingIsNotPresent() {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    when(dataCollectionInfo.getEncryptableSetting()).thenReturn(Optional.empty());
    abstractDataCollectionTask.run(dataCollectionInfo);
    verifyNoInteractions(encryptionService);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCorrectTaskResultIfNoFailure()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             TimeoutException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    DelegateResponseData responseData = abstractDataCollectionTask.run(dataCollectionInfo);

    DataCollectionTaskResult taskResult = (DataCollectionTaskResult) responseData;
    assertThat(DataCollectionTaskStatus.SUCCESS).isEqualTo(taskResult.getStatus());
    assertThat(DelegateStateType.SPLUNKV2).isEqualTo(taskResult.getStateType());
    assertThat(taskResult.getErrorMessage()).isNull();
    verify(cvTaskService)
        .updateCVTaskStatus(dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvTaskId(), taskResult);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCorrectTaskResultIfLessThenRetryCountFailures()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             DataCollectionException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    doThrow(new RuntimeException("error message from test")).doNothing().when(dataCollector).init(any(), any());
    DelegateResponseData responseData = abstractDataCollectionTask.run(dataCollectionInfo);

    DataCollectionTaskResult taskResult = (DataCollectionTaskResult) responseData;
    verify(dataCollector, times(2)).init(any(), eq(dataCollectionInfo));
    verify(abstractDataCollectionTask, times(1)).collectAndSaveData(dataCollectionInfo);
    assertThat(taskResult.getStatus()).isEqualTo(DataCollectionTaskStatus.SUCCESS);
    assertThat(taskResult.getStateType()).isEqualTo(DelegateStateType.SPLUNKV2);
    assertThat(taskResult.getErrorMessage()).isEqualTo("error message from test");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testStatusFailureInCaseOfExceptionOnInitWithRetryCount()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             DataCollectionException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    doThrow(new RuntimeException("error message from test")).when(dataCollector).init(any(), any());
    DelegateResponseData responseData = abstractDataCollectionTask.run(dataCollectionInfo);

    verify(dataCollector, times(3)).init(any(), eq(dataCollectionInfo));
    verify(abstractDataCollectionTask, times(0)).collectAndSaveData(dataCollectionInfo);
    DataCollectionTaskResult taskResult = (DataCollectionTaskResult) responseData;
    assertThat(DataCollectionTaskStatus.FAILURE).isEqualTo(taskResult.getStatus());
    assertThat(DelegateStateType.SPLUNKV2).isEqualTo(taskResult.getStateType());
    assertThat("error message from test").isEqualTo(taskResult.getErrorMessage());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testStatusFailureInCaseOfExceptionOnCollectAndSaveWithRetryCount()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             DataCollectionException, TimeoutException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    doThrow(new RuntimeException("error message from test")).when(abstractDataCollectionTask).collectAndSaveData(any());
    DelegateResponseData responseData = abstractDataCollectionTask.run(dataCollectionInfo);

    verify(dataCollector, times(3)).init(any(), eq(dataCollectionInfo));
    verify(abstractDataCollectionTask, times(3)).collectAndSaveData(dataCollectionInfo);
    DataCollectionTaskResult taskResult = (DataCollectionTaskResult) responseData;
    assertThat(taskResult.getStatus()).isEqualTo(DataCollectionTaskStatus.FAILURE);
    assertThat(taskResult.getStateType()).isEqualTo(DelegateStateType.SPLUNKV2);
    assertThat(taskResult.getErrorMessage()).isEqualTo("error message from test");
    verify(cvTaskService)
        .updateCVTaskStatus(dataCollectionInfo.getAccountId(), dataCollectionInfo.getCvTaskId(), taskResult);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testActivityLogOnTaskFailure()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             DataCollectionException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    doThrow(new RuntimeException("error message from test")).when(abstractDataCollectionTask).collectAndSaveData(any());
    abstractDataCollectionTask.run(dataCollectionInfo);
    verify(logger, times(1)).error(eq("Data collection failed with exception: error message from test"));
    verify(logger, times(3)).warn(eq("[Retrying] Data collection task failed with exception: error message from test"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testActivityLogOnTaskSuccess()
      throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException,
             DataCollectionException {
    DataCollectionInfoV2 dataCollectionInfo = createDataCollectionInfo();
    DataCollector<DataCollectionInfoV2> dataCollector = mock(DataCollector.class);
    doReturn(dataCollector).when(dataCollectorFactory).newInstance(any());
    abstractDataCollectionTask.run(dataCollectionInfo);
    verify(logger, times(1)).info(eq("Starting data collection."));
    verify(logger, times(1)).info(eq("Finished data collection with status: SUCCESS"));
  }

  private DataCollectionInfoV2 createDataCollectionInfo() {
    DelegateStateType stateType = DelegateStateType.SPLUNKV2;
    DataCollectionInfoV2 dataCollectionInfoV2 = mock(DataCollectionInfoV2.class);
    when(dataCollectionInfoV2.getStateType()).thenReturn(stateType);
    when(dataCollectionInfoV2.getEncryptableSetting()).thenReturn(Optional.empty());
    when(dataCollectionInfoV2.getEncryptedDataDetails()).thenReturn(Lists.newArrayList());
    when(dataCollectionInfoV2.getAccountId()).thenReturn(UUID.randomUUID().toString());
    when(dataCollectionInfoV2.getStateExecutionId()).thenReturn(UUID.randomUUID().toString());
    when(dataCollectionInfoV2.getStartTime()).thenReturn(Instant.now().minus(5, ChronoUnit.MINUTES));
    when(dataCollectionInfoV2.getEndTime()).thenReturn(Instant.now());
    when(dataCollectionInfoV2.getCvTaskId()).thenReturn(UUID.randomUUID().toString());
    return dataCollectionInfoV2;
  }

  // TODO: write test for saving and create third party call logs.
}
