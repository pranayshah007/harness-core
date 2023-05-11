/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.steadystate.model.K8sStatusWatchDTO;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import io.kubernetes.client.openapi.ApiException;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class AbstractWorkloadWatcherTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final K8sStatusWatchDTO watchDTO = K8sStatusWatchDTO.builder().build();
  private final KubernetesResourceId workloadId = KubernetesResourceId.builder().build();

  @Spy private AbstractWorkloadWatcher abstractWorkloadWatcher;

  @Mock private LogCallback logCallback;

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusSuccessful() {
    doReturn(true).when(abstractWorkloadWatcher).watchRolloutStatusInternal(watchDTO, workloadId, logCallback);
    boolean result = abstractWorkloadWatcher.watchRolloutStatus(watchDTO, workloadId, logCallback);
    assertThat(result).isTrue();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusApiException() {
    testWatchRolloutStatusFailure(new ApiException("Something went wrong"), false);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusKubernetesCliTaskRuntimeException() {
    testWatchRolloutStatusFailure(
        new KubernetesCliTaskRuntimeException("Failed to watch", KubernetesCliCommandType.STEADY_STATE_CHECK), false);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusRuntimeException() {
    testWatchRolloutStatusFailure(new RuntimeException("Unexpected"), true);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusInterruptedException() {
    testWatchRolloutStatusFailure(new InterruptedException(), false);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWatchRolloutStatusUnhandledException() {
    testWatchRolloutStatusFailure(new UnknownTestException(), false);
  }

  @SneakyThrows
  private void testWatchRolloutStatusFailure(Exception exception, boolean throwEvenIfErrorFrameworkDisabled) {
    doThrow(exception).when(abstractWorkloadWatcher).watchRolloutStatusInternal(watchDTO, workloadId, logCallback);

    // error framework disabled
    watchDTO.setErrorFrameworkEnabled(false);
    if (throwEvenIfErrorFrameworkDisabled) {
      assertThatThrownBy(() -> abstractWorkloadWatcher.watchRolloutStatus(watchDTO, workloadId, logCallback))
          .isInstanceOf(exception.getClass());
    } else {
      boolean result = abstractWorkloadWatcher.watchRolloutStatus(watchDTO, workloadId, logCallback);
      assertThat(result).isFalse();
    }

    // error framework enabled
    watchDTO.setErrorFrameworkEnabled(true);
    assertThatThrownBy(() -> abstractWorkloadWatcher.watchRolloutStatusInternal(watchDTO, workloadId, logCallback))
        .isInstanceOf(exception.getClass());
  }

  private static class UnknownTestException extends Exception {};
}