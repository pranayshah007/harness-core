/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.steadystate.watcher.workload;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class K8sWorkloadWatcherFactoryTest extends CategoryTest {
  @InjectMocks K8sWorkloadWatcherFactory k8sWorkloadWatcherFactory;
  @Mock private DeploymentApiWatcher deploymentApiWatcher;
  @Mock private StatefulSetApiWatcher statefulSetApiWatcher;
  @Mock private DaemonSetApiWatcher daemonSetApiWatcher;
  @Mock private JobApiWatcher jobApiWatcher;
  @Mock private DeploymentConfigCliWatcher deploymentConfigCliWatcher;
  @Mock private JobCliWatcher jobCliWatcher;
  @Mock private K8sCliWatcher k8sCliWatcher;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFetchingApiWorkloadWatcher() {
    WorkloadWatcher workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("Deployment", true);
    assertThat(workloadWatcher).isInstanceOf(DeploymentApiWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("StatefulSet", true);
    assertThat(workloadWatcher).isInstanceOf(StatefulSetApiWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("DaemonSet", true);
    assertThat(workloadWatcher).isInstanceOf(DaemonSetApiWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("DeploymentConfig", true);
    assertThat(workloadWatcher).isInstanceOf(DeploymentConfigCliWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("Job", true);
    assertThat(workloadWatcher).isInstanceOf(JobApiWatcher.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFetchingCliWorkloadWatcher() {
    WorkloadWatcher workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("Deployment", false);
    assertThat(workloadWatcher).isInstanceOf(K8sCliWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("StatefulSet", false);
    assertThat(workloadWatcher).isInstanceOf(K8sCliWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("DaemonSet", false);
    assertThat(workloadWatcher).isInstanceOf(K8sCliWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("DeploymentConfig", false);
    assertThat(workloadWatcher).isInstanceOf(DeploymentConfigCliWatcher.class);

    workloadWatcher = k8sWorkloadWatcherFactory.getWorkloadWatcher("Job", false);
    assertThat(workloadWatcher).isInstanceOf(JobCliWatcher.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testInvalidWorkloadType() {
    assertThatThrownBy(() -> k8sWorkloadWatcherFactory.getWorkloadWatcher("UnsupportedWorkload", true))
        .matches(throwable -> {
          assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
          assertThat(throwable.getMessage()).contains("No enum constant");
          return true;
        });

    assertThatThrownBy(() -> k8sWorkloadWatcherFactory.getWorkloadWatcher("UnsupportedWorkload", false))
        .matches(throwable -> {
          assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
          assertThat(throwable.getMessage()).contains("No enum constant");
          return true;
        });
  }
}
