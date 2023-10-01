/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskServiceClientRegistry;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.k8s.watch.K8sWatchPerpetualTaskServiceClient;
import io.harness.rule.Owner;

import software.wings.service.intfc.DelegateService;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class PerpetualTaskRecordHandlerTest extends CategoryTest {
  private String accountId = "ACCOUNT_ID";
  private String taskId = "TASK_ID";
  private String delegateId = "DELEGATE_ID";
  private PerpetualTaskRecord record;

  @Mock private DelegateTask delegateTask;
  @Mock K8sWatchPerpetualTaskServiceClient k8sWatchPerpetualTaskServiceClient;
  @Mock PerpetualTaskServiceClientRegistry clientRegistry;
  @Mock DelegateService delegateService;
  @Mock PerpetualTaskService perpetualTaskService;
  @Mock PerpetualTaskRecordDao perpetualTaskRecordDao;
  @InjectMocks PerpetualTaskRecordHandler perpetualTaskRecordHandler;
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() throws InterruptedException {
    Map<String, String> clientParams = new HashMap<>();
    clientParams.put("test", "test");
    record = PerpetualTaskRecord.builder()
                 .accountId(accountId)
                 .uuid(taskId)
                 .perpetualTaskType(PerpetualTaskType.K8S_WATCH)
                 .clientContext(PerpetualTaskClientContext.builder().clientParams(clientParams).build())
                 .build();
    when(clientRegistry.getClient(isA(String.class))).thenReturn(k8sWatchPerpetualTaskServiceClient);
    when(k8sWatchPerpetualTaskServiceClient.getValidationTask(isA(PerpetualTaskClientContext.class), anyString()))
        .thenReturn(delegateTask);

    doNothing().when(perpetualTaskService).appointDelegate(eq(accountId), anyString(), eq(delegateId), anyLong());
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testPerpetualTaskServiceClientGetValidationTask() {
    Map<String, String> clientParams = new HashMap<>();
    clientParams.put("test", "test");
    PerpetualTaskRecord record =
        PerpetualTaskRecord.builder()
            .accountId(accountId)
            .uuid(taskId)
            .perpetualTaskType(PerpetualTaskType.K8S_WATCH)
            .clientContext(PerpetualTaskClientContext.builder().clientParams(clientParams).build())
            .build();

    when(clientRegistry.getClient(isA(String.class))).thenReturn(k8sWatchPerpetualTaskServiceClient);
    when(k8sWatchPerpetualTaskServiceClient.getValidationTask(isA(PerpetualTaskClientContext.class), anyString()))
        .thenReturn(delegateTask);

    DelegateTask realDelegateTask = perpetualTaskRecordHandler.getValidationTask(record);

    assertThat(realDelegateTask).isNotNull();
    assertThat(realDelegateTask).isEqualTo(delegateTask);

    verify(k8sWatchPerpetualTaskServiceClient, atLeastOnce())
        .getValidationTask(isA(PerpetualTaskClientContext.class), anyString());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldNotHandle() throws InterruptedException {
    RemoteMethodReturnValueData response = RemoteMethodReturnValueData.builder().build();
    when(delegateService.executeTaskV2(isA(DelegateTask.class))).thenReturn(response);
    perpetualTaskRecordHandler.assign(record);
    verify(perpetualTaskService, times(0)).appointDelegate(eq(accountId), anyString(), eq(delegateId), anyLong());
  }
}
