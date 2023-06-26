/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.helm.beans.FetchHelmChartManifestRequest;
import io.harness.delegate.task.helm.request.HelmFetchChartManifestTaskParameters;
import io.harness.delegate.task.helm.response.HelmChartManifest;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import java.io.File;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class HelmFetchChartManifestTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private HelmChartManifestTaskService helmChartManifestTaskService;

  @InjectMocks
  private HelmFetchChartManifestTask helmFetchChartManifestTask =
      new HelmFetchChartManifestTask(DelegateTaskPackage.builder()
                                         .delegateId("delegateid")
                                         .data(TaskData.builder().timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                         .build(),
          logStreamingTaskClient, notifyResponseData -> {}, () -> true);

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunUnsupported() {
    assertThatThrownBy(() -> helmFetchChartManifestTask.run(new Object[] {new Object(), new Object()}))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This method is deprecated. Use run(TaskParameters) instead.");
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRun() {
    final HelmChartManifest result = HelmChartManifest.builder().build();

    doAnswer(invocation -> {
      FetchHelmChartManifestRequest request = invocation.getArgument(0);
      assertThat(request.getWorkingDirectory()).isNotNull();
      assertThat(new File(request.getWorkingDirectory())).exists();
      return result;
    })
        .when(helmChartManifestTaskService)
        .fetchHelmChartManifest(any(FetchHelmChartManifestRequest.class));

    helmFetchChartManifestTask.run(HelmFetchChartManifestTaskParameters.builder().build());

    ArgumentCaptor<FetchHelmChartManifestRequest> requestCaptor =
        ArgumentCaptor.forClass(FetchHelmChartManifestRequest.class);

    verify(helmChartManifestTaskService).fetchHelmChartManifest(requestCaptor.capture());
    FetchHelmChartManifestRequest expectedRequest = requestCaptor.getValue();

    assertThat(new File(expectedRequest.getWorkingDirectory())).doesNotExist();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunException() {
    doThrow(new InvalidRequestException("Something went wrong"))
        .when(helmChartManifestTaskService)
        .fetchHelmChartManifest(any(FetchHelmChartManifestRequest.class));

    assertThatThrownBy(() -> helmFetchChartManifestTask.run(HelmFetchChartManifestTaskParameters.builder().build()))
        .isInstanceOf(InvalidRequestException.class);
  }
}