/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.perpetualtask.PerpetualTaskServiceImpl.MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.service.impl.PerpetualTaskCapabilityCheckResponse;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PerpetualTaskValidationCallbackTest extends WingsBaseTest {
  private String perpetualTaskId = generateUuid();
  private String accountId = generateUuid();
  private PerpetualTaskValidationCallback callback;
  @Inject private HPersistence hPersistence;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;

  @Before
  public void setUp() throws IllegalAccessException {
    hPersistence.save(PerpetualTaskRecord.builder()
                          .uuid(perpetualTaskId)
                          .accountId(accountId)
                          .state(PerpetualTaskState.TASK_UNASSIGNED)
                          .build());
    callback = new PerpetualTaskValidationCallback(accountId, perpetualTaskId, generateUuid());
    FieldUtils.writeField(callback, "hPersistence", hPersistence, true);
    FieldUtils.writeField(callback, "perpetualTaskService", perpetualTaskService, true);
    FieldUtils.writeField(callback, "perpetualTaskRecordDao", perpetualTaskRecordDao, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNotifyErrorUpdatesAssignTryCountAndException() {
    ErrorNotifyResponseData notifyResponseData = ErrorNotifyResponseData.builder().errorMessage(generateUuid()).build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(generateUuid(), notifyResponseData);
    callback.notifyError(response);
    PerpetualTaskRecord perpetualTaskRecord = hPersistence.get(PerpetualTaskRecord.class, perpetualTaskId);
    assertThat(perpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_UNASSIGNED);
    assertThat(perpetualTaskRecord.getAssignTryCount()).isEqualTo(1);
    assertThat(perpetualTaskRecord.getException()).isEqualTo(notifyResponseData.getErrorMessage());
    assertThat(perpetualTaskRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.PT_TASK_FAILED);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNotifyErrorUpdatesSatate() {
    hPersistence.update(hPersistence.createQuery(PerpetualTaskRecord.class),
        hPersistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.assignTryCount, MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT));
    ErrorNotifyResponseData notifyResponseData = ErrorNotifyResponseData.builder().errorMessage(generateUuid()).build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(generateUuid(), notifyResponseData);
    callback.notifyError(response);
    PerpetualTaskRecord perpetualTaskRecord = hPersistence.get(PerpetualTaskRecord.class, perpetualTaskId);
    assertThat(perpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(perpetualTaskRecord.getAssignTryCount()).isEqualTo(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT + 1);
    assertThat(perpetualTaskRecord.getException()).isEqualTo(notifyResponseData.getErrorMessage());
    assertThat(perpetualTaskRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.PT_TASK_FAILED);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNotifyNotAssignable() {
    hPersistence.update(hPersistence.createQuery(PerpetualTaskRecord.class),
        hPersistence.createUpdateOperations(PerpetualTaskRecord.class)
            .set(PerpetualTaskRecordKeys.assignTryCount, MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT));

    DelegateTaskNotifyResponseData notifyResponseData =
        PerpetualTaskCapabilityCheckResponse.builder().ableToExecutePerpetualTask(false).build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(generateUuid(), notifyResponseData);
    callback.notifyError(response);
    PerpetualTaskRecord perpetualTaskRecord = hPersistence.get(PerpetualTaskRecord.class, perpetualTaskId);
    assertThat(perpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(perpetualTaskRecord.getAssignTryCount()).isEqualTo(MAX_FIBONACCI_INDEX_FOR_TASK_ASSIGNMENT + 1);
    assertThat(perpetualTaskRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.PT_TASK_FAILED);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNotifyGenericException() {
    RemoteMethodReturnValueData notifyResponseData =
        RemoteMethodReturnValueData.builder().exception(new RuntimeException("abc")).build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(generateUuid(), notifyResponseData);
    callback.notifyError(response);
    PerpetualTaskRecord perpetualTaskRecord = hPersistence.get(PerpetualTaskRecord.class, perpetualTaskId);
    assertThat(perpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_NON_ASSIGNABLE);
    assertThat(perpetualTaskRecord.getUnassignedReason()).isEqualTo(PerpetualTaskUnassignedReason.PT_TASK_FAILED);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNotifyAssign() {
    DelegateMetaInfo metaInfo = DelegateMetaInfo.builder().id(generateUuid()).build();
    DelegateTaskNotifyResponseData notifyResponseData = PerpetualTaskCapabilityCheckResponse.builder()
                                                            .ableToExecutePerpetualTask(true)
                                                            .delegateMetaInfo(metaInfo)
                                                            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(generateUuid(), notifyResponseData);
    callback.notifyError(response);
    PerpetualTaskRecord perpetualTaskRecord = hPersistence.get(PerpetualTaskRecord.class, perpetualTaskId);
    assertThat(perpetualTaskRecord.getState()).isEqualTo(PerpetualTaskState.TASK_ASSIGNED);
    assertThat(perpetualTaskRecord.getDelegateId()).isEqualTo(metaInfo.getId());
  }
}
