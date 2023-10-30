/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s;

import static io.harness.k8s.K8sReleaseWarningLogger.RELEASE_CONFLICT_WARNING_MESSAGE;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.ReleaseMetadata;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class K8sReleaseWarningLoggerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  ReleaseMetadata releaseMetadata1 = ReleaseMetadata.builder().serviceId("svcId1").build();

  ReleaseMetadata releaseMetadata2 = ReleaseMetadata.builder().serviceId("svcId2").build();

  @Mock IK8sReleaseHistory releaseHistory;

  @Mock LogCallback logCallback;

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testLogging() {
    String DIFF = "diff message";
    try (MockedStatic<K8sReleaseDiffCalculator> mockedStatic = mockStatic(K8sReleaseDiffCalculator.class)) {
      mockedStatic.when(() -> K8sReleaseDiffCalculator.releaseConflicts(releaseMetadata1, releaseHistory, false))
          .thenReturn(true);
      mockedStatic.when(() -> K8sReleaseDiffCalculator.getPreviousReleaseMetadata(releaseHistory, false))
          .thenReturn(releaseMetadata2);
      mockedStatic.when(() -> K8sReleaseDiffCalculator.calculateDiffForLogging(releaseMetadata1, releaseMetadata2))
          .thenReturn(DIFF);
      doNothing().when(logCallback).saveExecutionLog(anyString(), any(LogLevel.class));

      K8sReleaseWarningLogger.logWarningIfReleaseConflictExists(releaseMetadata1, releaseHistory, logCallback);
      verify(logCallback, times(1)).saveExecutionLog(String.format(RELEASE_CONFLICT_WARNING_MESSAGE, DIFF), WARN);
    }
  }
}
