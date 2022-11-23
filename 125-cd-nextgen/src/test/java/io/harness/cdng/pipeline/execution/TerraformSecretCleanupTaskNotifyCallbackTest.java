/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.executions.TerraformSecretCleanupTaskNotifyCallback;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.terraform.cleanup.TerraformSecretCleanupFailureDetails;
import io.harness.delegate.task.terraform.cleanup.TerraformSecretCleanupTaskResponse;
import io.harness.exception.TerraformSecretCleanupFailureException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.tasks.ResponseData;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.slf4j.LoggerFactory;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class TerraformSecretCleanupTaskNotifyCallbackTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Spy TerraformSecretCleanupTaskNotifyCallback notifyCallback = new TerraformSecretCleanupTaskNotifyCallback();

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testNotify() {
    DelegateResponseData notifyResponseData = TerraformSecretCleanupTaskResponse.builder()
                                                  .responseDataUuid("test-cleanup-uuid-1")
                                                  .secretCleanupFailureDetailsList(Collections.emptyList())
                                                  .build();

    Logger fooLogger = (Logger) LoggerFactory.getLogger(TerraformSecretCleanupTaskNotifyCallback.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);

    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("key", () -> notifyResponseData);

    notifyCallback.notify(responseSupplier);
    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(0).getFormattedMessage())
        .isEqualTo("Received success response terraform secret cleanup for cleanup Uuid: test-cleanup-uuid-1");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testNotifyWithException() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    Logger fooLogger = (Logger) LoggerFactory.getLogger(TerraformSecretCleanupTaskNotifyCallback.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);

    responseSupplier.put("key", () -> {
      throw new TerraformSecretCleanupFailureException(
          "test message - failed to cleanup secret from vault", new Throwable("runtimeExceptionContext"));
    });

    notifyCallback.notify(responseSupplier);
    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(0).getFormattedMessage())
        .isEqualTo("Failure Message: test message - failed to cleanup secret from vault and exception {}");
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testNotifyWithListOfExceptionErrorMessage() {
    DelegateResponseData notifyResponseData =
        TerraformSecretCleanupTaskResponse.builder()
            .responseDataUuid("test-cleanup-uuid-1")
            .secretCleanupFailureDetailsList(
                List.of(TerraformSecretCleanupFailureDetails.builder()
                            .encryptedRecordData(EncryptedRecordData.builder().uuid("uuid-123").build())
                            .exceptionMessage("errorConnectionMessage")
                            .build()))
            .build();

    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    Logger fooLogger = (Logger) LoggerFactory.getLogger(TerraformSecretCleanupTaskNotifyCallback.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);

    responseSupplier.put("key", () -> notifyResponseData);

    notifyCallback.notify(responseSupplier);
    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(0).getFormattedMessage())
        .isEqualTo(
            "Failed to cleanup terraform plan secret with uuid: uuid-123 because of exception message: errorConnectionMessage");
  }
}
