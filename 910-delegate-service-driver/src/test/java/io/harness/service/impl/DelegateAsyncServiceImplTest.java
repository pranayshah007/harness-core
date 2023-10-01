/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.DelegateServiceDriverTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateAsyncTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateAsyncService;
import io.harness.tasks.ResponseData;
import io.harness.waiter.StringNotifyResponseData;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DelegateAsyncServiceImplTest extends DelegateServiceDriverTestBase {
  @Inject private DelegateAsyncService delegateAsyncService;
  @Inject private HPersistence hPersistence;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  /**
   * This test is for when the record is inserted from the timeout method and when the task completes it will override
   * the details
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldInsertTaskResponse() {
    String taskId = generateUuid();
    long currentTimeStamp = System.currentTimeMillis();

    // Setting expiry to current time + 1hr
    long expiryEpoch = currentTimeStamp + Duration.ofMinutes(60).toMillis();

    // Setting hold until to current time + 2hr
    long holdUntil = currentTimeStamp + Duration.ofMinutes(120).toMillis();

    delegateAsyncService.setupTimeoutForTask(taskId, expiryEpoch, holdUntil);
    DelegateAsyncTaskResponse insertedTaskResponse = hPersistence.get(DelegateAsyncTaskResponse.class, taskId);

    assertThat(insertedTaskResponse).isNotNull();

    Date minValidUntil = Date.from(Instant.ofEpochMilli(expiryEpoch).plusSeconds(Duration.ofHours(1).getSeconds()));

    assertThat(insertedTaskResponse).isNotNull();
    assertThat(insertedTaskResponse.getProcessAfter()).isEqualTo(expiryEpoch);
    assertThat(insertedTaskResponse.getValidUntil()).isAfterOrEqualTo(minValidUntil);

    ResponseData responseData =
        (ResponseData) referenceFalseKryoSerializer.asInflatedObject(insertedTaskResponse.getResponseData());
    assertThat(responseData).isInstanceOf(ErrorNotifyResponseData.class);

    ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
    assertThat(errorNotifyResponseData.getErrorMessage())
        .isEqualTo("Delegate service did not provide response and the task time-outed");
  }

  /**
   * This test is when the response was so fast that it came even before the timeout method inserted the record
   */

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldUpsertTaskResponse() {
    // Inserting the record before event setting up the timeout details
    String taskId = generateUuid();
    long currentTimeStamp = System.currentTimeMillis();
    Date minValidUntil =
        Date.from(Instant.ofEpochMilli(currentTimeStamp).plusSeconds(Duration.ofHours(24).getSeconds()));
    String savedTaskId = hPersistence.save(DelegateAsyncTaskResponse.builder()
                                               .uuid(taskId)
                                               .processAfter(currentTimeStamp)
                                               .responseData(referenceFalseKryoSerializer.asDeflatedBytes(
                                                   StringNotifyResponseData.builder().data("DATA_FROM_TASK").build()))
                                               .build());

    assertThat(savedTaskId).isEqualTo(taskId);

    // Setting expiry to current time + 1hr
    long expiryEpoch = currentTimeStamp + Duration.ofMinutes(60).toMillis();

    // Setting hold until to current time + 2hr
    long holdUntil = currentTimeStamp + Duration.ofMinutes(120).toMillis();

    delegateAsyncService.setupTimeoutForTask(taskId, expiryEpoch, holdUntil);

    // Fetch the record from the database and assert
    DelegateAsyncTaskResponse upsertedTaskResponse = hPersistence.get(DelegateAsyncTaskResponse.class, taskId);
    assertThat(upsertedTaskResponse).isNotNull();

    // Process after need to be set from the original response and should not be overridden
    assertThat(upsertedTaskResponse.getProcessAfter()).isEqualTo(currentTimeStamp);

    // Valid Until should be from original
    assertThat(upsertedTaskResponse.getValidUntil()).isAfter(minValidUntil);

    ResponseData responseData =
        (ResponseData) referenceFalseKryoSerializer.asInflatedObject(upsertedTaskResponse.getResponseData());
    assertThat(responseData).isInstanceOf(StringNotifyResponseData.class);

    StringNotifyResponseData stringNotifyResponseData = (StringNotifyResponseData) responseData;
    assertThat(stringNotifyResponseData.getData()).isEqualTo("DATA_FROM_TASK");
  }

  /**
   * This test is when the response was so fast that it came even before the timeout method inserted the record
   */

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void shouldUpsertTaskResponseAndNullUsingKryoWithoutReference() {
    // Inserting the record before event setting up the timeout details
    String taskId = generateUuid();
    long currentTimeStamp = System.currentTimeMillis();
    Date minValidUntil =
        Date.from(Instant.ofEpochMilli(currentTimeStamp).plusSeconds(Duration.ofHours(24).getSeconds()));
    String savedTaskId = hPersistence.save(DelegateAsyncTaskResponse.builder()
                                               .uuid(taskId)
                                               .processAfter(currentTimeStamp)
                                               .usingKryoWithoutReference(null)
                                               .responseData(referenceFalseKryoSerializer.asDeflatedBytes(
                                                   StringNotifyResponseData.builder().data("DATA_FROM_TASK").build()))
                                               .build());

    assertThat(savedTaskId).isEqualTo(taskId);

    // Setting expiry to current time + 1hr
    long expiryEpoch = currentTimeStamp + Duration.ofMinutes(60).toMillis();

    // Setting hold until to current time + 2hr
    long holdUntil = currentTimeStamp + Duration.ofMinutes(120).toMillis();

    delegateAsyncService.setupTimeoutForTask(taskId, expiryEpoch, holdUntil);

    // Fetch the record from the database and assert
    DelegateAsyncTaskResponse upsertedTaskResponse = hPersistence.get(DelegateAsyncTaskResponse.class, taskId);
    assertThat(upsertedTaskResponse).isNotNull();

    // Process after need to be set from the original response and should not be overridden
    assertThat(upsertedTaskResponse.getProcessAfter()).isEqualTo(currentTimeStamp);

    // Valid Until should be from original
    assertThat(upsertedTaskResponse.getValidUntil()).isAfter(minValidUntil);

    ResponseData responseData =
        (ResponseData) referenceFalseKryoSerializer.asInflatedObject(upsertedTaskResponse.getResponseData());
    assertThat(responseData).isInstanceOf(StringNotifyResponseData.class);

    StringNotifyResponseData stringNotifyResponseData = (StringNotifyResponseData) responseData;
    assertThat(stringNotifyResponseData.getData()).isEqualTo("DATA_FROM_TASK");
  }
}
