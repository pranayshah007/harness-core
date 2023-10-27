/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.event;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class K8sApiEventMatcherTest extends CategoryTest {
  private final K8sApiEventMatcher k8sApiEventMatcher = new K8sApiEventMatcher();

  private final OffsetDateTime N = OffsetDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
  private final OffsetDateTime N_MINUS_1 = OffsetDateTime.of(2000, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC);
  private final OffsetDateTime N_MINUS_2 = OffsetDateTime.of(2000, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
  private final OffsetDateTime N_PLUS_1 = OffsetDateTime.of(2000, 1, 1, 13, 0, 0, 0, ZoneOffset.UTC);
  private final OffsetDateTime N_PLUS_2 = OffsetDateTime.of(2000, 1, 1, 14, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEventTimeBoundTests() {
    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_PLUS_1), N)).isTrue();
    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_MINUS_1), N)).isFalse();

    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_MINUS_1, N_PLUS_1), N))
        .isTrue();
    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_MINUS_1, N_PLUS_1), N_MINUS_2))
        .isFalse();
    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_MINUS_1, N_PLUS_1), N_PLUS_2))
        .isFalse();

    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(null, N_PLUS_1), N)).isTrue();
    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(null, N_PLUS_1), N_PLUS_2))
        .isFalse();

    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_PLUS_1, null), N_PLUS_2))
        .isTrue();
    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(createEvent(N_PLUS_1, null), N)).isFalse();

    Assertions.assertThat(k8sApiEventMatcher.isEventEmittedPostDeployment(new CoreV1Event(), N)).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testEventLogger() {
    verifyEventLogging("INFO");
    verifyEventLogging("WARNING");
  }

  private void verifyEventLogging(String eventType) {
    String infoFormat = "SOME_INFO_FORMAT";
    String warningFormat = "SOME_WARNING_FORMAT";
    int expectedInvocationsInfo = eventType.equals("WARNING") ? 0 : 1;
    int expectedInvocationsWarning = eventType.equals("WARNING") ? 1 : 0;
    LogCallback logCallback = mock(LogCallback.class);
    CoreV1Event event = new CoreV1Event().involvedObject(new V1ObjectReference().name("Test")).type(eventType);
    k8sApiEventMatcher.logEvents(event, logCallback, infoFormat, warningFormat);

    verify(logCallback, times(expectedInvocationsInfo)).saveExecutionLog(infoFormat);
    verify(logCallback, times(expectedInvocationsWarning)).saveExecutionLog(warningFormat);
  }

  private CoreV1Event createEvent(OffsetDateTime time) {
    return new CoreV1Event().eventTime(time);
  }

  private CoreV1Event createEvent(OffsetDateTime start, OffsetDateTime end) {
    return new CoreV1Event().firstTimestamp(start).lastTimestamp(end);
  }
}
