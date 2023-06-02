/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.executions.TerraformCloudCleanupTaskNotifyCallback;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.task.terraformcloud.cleanup.TerraformCloudCleanupTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
public class TerraformCloudCleanupTaskNotifyCallbackTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Spy TerraformCloudCleanupTaskNotifyCallback notifyCallback = new TerraformCloudCleanupTaskNotifyCallback();

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNotify() {
    DelegateResponseData notifyResponseData = TerraformCloudCleanupTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                  .runId("run-id")
                                                  .build();
    Logger fooLogger = (Logger) LoggerFactory.getLogger(TerraformCloudCleanupTaskNotifyCallback.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    responseSupplier.put("key", () -> notifyResponseData);

    notifyCallback.notify(responseSupplier);

    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(0).getFormattedMessage())
        .isEqualTo("Received success response terraform cloud cleanup for runId: run-id");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNotifyFailure() {
    DelegateResponseData notifyResponseData = TerraformCloudCleanupTaskResponse.builder()
                                                  .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                  .errorMessage("error message")
                                                  .runId("run-id")
                                                  .build();
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    Logger fooLogger = (Logger) LoggerFactory.getLogger(TerraformCloudCleanupTaskNotifyCallback.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);
    responseSupplier.put("key", () -> notifyResponseData);

    notifyCallback.notify(responseSupplier);

    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(0).getFormattedMessage())
        .isEqualTo("Failed to discard run with runId: run-id because of: error message");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNotifyException() {
    Map<String, Supplier<ResponseData>> responseSupplier = new HashMap<>();
    Logger fooLogger = (Logger) LoggerFactory.getLogger(TerraformCloudCleanupTaskNotifyCallback.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    fooLogger.addAppender(listAppender);
    responseSupplier.put("key", () -> { throw new InvalidRequestException("error message"); });

    notifyCallback.notify(responseSupplier);

    List<ILoggingEvent> logList = listAppender.list;
    assertThat(logList.get(0).getFormattedMessage())
        .isEqualTo("Something went wrong for terraform cloud cleanup: error message");
  }
}
