/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.beans.K8sApplyHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.Kind;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sApplyBaseHandlerTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks private K8sApplyBaseHandler baseHandler;
  @Mock private LogCallback logCallback;
  private final Integer timeoutIntervalInMin = 10;
  private final long timeoutIntervalInMillis = 60 * timeoutIntervalInMin * 1000;
  private static final String namespace = "default";
  private final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();

  private ImmutableSet managedWorkloadsKinds = ImmutableSet.of(Kind.Deployment.name(), Kind.StatefulSet.name(),
      Kind.DaemonSet.name(), Kind.Job.name(), Kind.DeploymentConfig.name());
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPerformSteadyCheckOnlyForManagedWorkloads() throws Exception {
    KubernetesResourceId deployment = KubernetesResourceId.builder().kind("Deployment").name("test-deployment").build();
    List<KubernetesResource> workloads =
        Arrays.asList(KubernetesResource.builder().spec("Spec").resourceId(deployment).build());
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    Kubectl client = mock(Kubectl.class);
    config.setClient(client);
    config.setWorkloads(workloads);
    config.setCustomWorkloads(Collections.emptyList());

    ArgumentCaptor<List> resourceIdCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> customResourceIdCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(
            any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(namespace), eq(logCallback), eq(true), eq(false));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(logCallback),
            eq(true), eq(timeoutIntervalInMillis), eq(false));
    boolean result = baseHandler.steadyStateCheck(
        false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isTrue();

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(any(Kubectl.class), resourceIdCaptor.capture(), eq(delegateTaskParams),
            eq(namespace), eq(logCallback), eq(true), eq(false));
    List k8sResourceIds = resourceIdCaptor.getValue();
    assertThat(k8sResourceIds).containsExactly(deployment);
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(any(Kubectl.class), customResourceIdCaptor.capture(),
            eq(delegateTaskParams), eq(logCallback), eq(true), eq(timeoutIntervalInMillis), eq(false));
    List customResourceIds = customResourceIdCaptor.getValue();
    assertThat(customResourceIds).isEmpty();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPerformSteadyCheckOnlyForCustomWorkloads() throws Exception {
    KubernetesResourceId crd = KubernetesResourceId.builder().kind("Custom").name("test-crd").versioned(false).build();
    KubernetesResource crdResource = KubernetesResource.builder().spec("Spec").resourceId(crd).build();
    List<KubernetesResource> workloads = Arrays.asList(crdResource);
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    Kubectl client = mock(Kubectl.class);
    config.setClient(client);
    config.setWorkloads(Collections.emptyList());
    config.setCustomWorkloads(workloads);

    ArgumentCaptor<List> resourceIdCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> customResourceIdCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(namespace),
            eq(logCallback), eq(false), eq(false));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(logCallback),
            eq(true), eq(timeoutIntervalInMillis), eq(false));
    boolean result = baseHandler.steadyStateCheck(
        false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isTrue();

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(any(Kubectl.class), resourceIdCaptor.capture(), eq(delegateTaskParams),
            eq(namespace), eq(logCallback), eq(false), eq(false));
    List k8sResourceIds = resourceIdCaptor.getValue();
    assertThat(k8sResourceIds).isEmpty();
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(any(Kubectl.class), customResourceIdCaptor.capture(),
            eq(delegateTaskParams), eq(logCallback), eq(true), eq(timeoutIntervalInMillis), eq(false));
    List customResourceIds = customResourceIdCaptor.getValue();
    assertThat(customResourceIds).containsExactly(crdResource);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldPerformSteadyCheckForManagedAndCustomWorkloads() throws Exception {
    KubernetesResourceId deployment =
        KubernetesResourceId.builder().kind("Deployment").name("test-deployment").versioned(false).build();
    KubernetesResource deploymentResource = KubernetesResource.builder().spec("Spec").resourceId(deployment).build();

    KubernetesResourceId crd = KubernetesResourceId.builder().kind("Custom").name("test-crd").versioned(false).build();
    KubernetesResource crdResource = KubernetesResource.builder().spec("Spec").resourceId(crd).build();

    List<KubernetesResource> customWorkloads = Arrays.asList(crdResource);
    List<KubernetesResource> managedWorkloads = Arrays.asList(deploymentResource);
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();

    Kubectl client = mock(Kubectl.class);
    config.setClient(client);
    config.setWorkloads(managedWorkloads);
    config.setCustomWorkloads(customWorkloads);

    ArgumentCaptor<List> resourceIdCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> customResourceIdCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(namespace),
            eq(logCallback), eq(false), eq(false));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(logCallback),
            eq(true), eq(timeoutIntervalInMillis), eq(false));
    boolean result = baseHandler.steadyStateCheck(
        false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isTrue();

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(any(Kubectl.class), resourceIdCaptor.capture(), eq(delegateTaskParams),
            eq(namespace), eq(logCallback), eq(false), eq(false));
    List k8sResourceIds = resourceIdCaptor.getValue();
    assertThat(k8sResourceIds).containsExactly(deployment);
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(any(Kubectl.class), customResourceIdCaptor.capture(),
            eq(delegateTaskParams), eq(logCallback), eq(true), eq(timeoutIntervalInMillis), eq(false));
    List customResourceIds = customResourceIdCaptor.getValue();
    assertThat(customResourceIds).containsExactly(crdResource);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldReturnFalseIfManagedWorkloadsSteadyCheckFails() throws Exception {
    KubernetesResourceId deployment =
        KubernetesResourceId.builder().kind("Deployment").name("test-deployment").versioned(false).build();
    KubernetesResource deploymentResource = KubernetesResource.builder().spec("Spec").resourceId(deployment).build();

    KubernetesResourceId crd = KubernetesResourceId.builder().kind("Custom").name("test-crd").versioned(false).build();
    KubernetesResource crdResource = KubernetesResource.builder().spec("Spec").resourceId(crd).build();

    List<KubernetesResource> customWorkloads = Arrays.asList(crdResource);
    List<KubernetesResource> managedWorkloads = Arrays.asList(deploymentResource);
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    Kubectl client = mock(Kubectl.class);
    config.setClient(client);
    config.setWorkloads(managedWorkloads);
    config.setCustomWorkloads(customWorkloads);

    ArgumentCaptor<List> resourceIdCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> customResourceIdCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(namespace),
            eq(logCallback), eq(false), eq(false));
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(logCallback),
            eq(true), eq(timeoutIntervalInMillis), eq(false));
    boolean result = baseHandler.steadyStateCheck(
        false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isFalse();

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(any(Kubectl.class), resourceIdCaptor.capture(), eq(delegateTaskParams),
            eq(namespace), eq(logCallback), eq(false), eq(false));
    List k8sResourceIds = resourceIdCaptor.getValue();
    assertThat(k8sResourceIds).containsExactly(deployment);
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(any(Kubectl.class), customResourceIdCaptor.capture(),
            eq(delegateTaskParams), eq(logCallback), eq(true), eq(timeoutIntervalInMillis), eq(false));
    List customResourceIds = customResourceIdCaptor.getValue();
    assertThat(customResourceIds).containsExactly(crdResource);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldReturnFalseIfCustomWorkloadsSteadyCheckFails() throws Exception {
    KubernetesResourceId deployment =
        KubernetesResourceId.builder().kind("Deployment").name("test-deployment").versioned(false).build();
    KubernetesResource deploymentResource = KubernetesResource.builder().spec("Spec").resourceId(deployment).build();

    KubernetesResourceId crd = KubernetesResourceId.builder().kind("Custom").name("test-crd").versioned(false).build();
    KubernetesResource crdResource = KubernetesResource.builder().spec("Spec").resourceId(crd).build();

    List<KubernetesResource> customWorkloads = Arrays.asList(crdResource);
    List<KubernetesResource> managedWorkloads = Arrays.asList(deploymentResource);
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    Kubectl client = mock(Kubectl.class);
    config.setClient(client);
    config.setWorkloads(managedWorkloads);
    config.setCustomWorkloads(customWorkloads);

    ArgumentCaptor<List> resourceIdCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List> customResourceIdCaptor = ArgumentCaptor.forClass(List.class);
    doReturn(true)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(namespace),
            eq(logCallback), eq(false), eq(false));
    doReturn(false)
        .when(k8sTaskHelperBase)
        .doStatusCheckForAllCustomResources(any(Kubectl.class), anyList(), eq(delegateTaskParams), eq(logCallback),
            eq(true), eq(timeoutIntervalInMillis), eq(false));
    boolean result = baseHandler.steadyStateCheck(
        false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isFalse();

    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllResources(any(Kubectl.class), resourceIdCaptor.capture(), eq(delegateTaskParams),
            eq(namespace), eq(logCallback), eq(false), eq(false));
    List k8sResourceIds = resourceIdCaptor.getValue();
    assertThat(k8sResourceIds).containsExactly(deployment);
    verify(k8sTaskHelperBase, times(1))
        .doStatusCheckForAllCustomResources(any(Kubectl.class), customResourceIdCaptor.capture(),
            eq(delegateTaskParams), eq(logCallback), eq(true), eq(timeoutIntervalInMillis), eq(false));
    List customResourceIds = customResourceIdCaptor.getValue();
    assertThat(customResourceIds).containsExactly(crdResource);
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldReturnTrueIfNoWorkloadsSpecified() throws Exception {
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    config.setWorkloads(Collections.emptyList());
    config.setCustomWorkloads(Collections.emptyList());

    boolean result = baseHandler.steadyStateCheck(
        false, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldReturnTrueIfSkipSteadyCheckIsTrue() throws Exception {
    KubernetesResourceId deployment =
        KubernetesResourceId.builder().kind("Deployment").name("test-deployment").versioned(false).build();
    KubernetesResource deploymentResource = KubernetesResource.builder().spec("Spec").resourceId(deployment).build();

    KubernetesResourceId crd = KubernetesResourceId.builder().kind("Custom").name("test-crd").versioned(false).build();
    KubernetesResource crdResource = KubernetesResource.builder().spec("Spec").resourceId(crd).build();

    List<KubernetesResource> customWorkloads = Arrays.asList(crdResource);
    List<KubernetesResource> managedWorkloads = Arrays.asList(deploymentResource);
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    config.setWorkloads(managedWorkloads);
    config.setCustomWorkloads(customWorkloads);

    boolean result = baseHandler.steadyStateCheck(
        true, namespace, delegateTaskParams, timeoutIntervalInMillis, logCallback, config, false, false, true);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldPrepareManagedAndCustomWorkloadsSuccess() {
    List<KubernetesResource> crd = ManifestHelper.processYaml("apiVersion: apps/v1\n"
        + "kind: Foo\n"
        + "metadata:\n"
        + "  name: test-custom-resource\n"
        + "  annotations:\n"
        + "    harness.io/managed-workload: true\n"
        + "    harness.io/steadyStateCondition: 1==1\n"
        + "spec:\n"
        + "  replicas: 1");

    List<KubernetesResource> allWorkloads = Arrays.asList(crd.get(0),
        KubernetesResource.builder()
            .spec("Spec")
            .resourceId(
                KubernetesResourceId.builder().kind("Deployment").name("test-deployment").versioned(false).build())
            .build(),
        KubernetesResource.builder()
            .spec("Spec")
            .resourceId(KubernetesResourceId.builder()
                            .kind("DeploymentConfig")
                            .name("test-deploymentconfig")
                            .versioned(false)
                            .build())
            .build(),
        KubernetesResource.builder()
            .spec("Spec")
            .resourceId(
                KubernetesResourceId.builder().kind("StatefulSet").name("test-statefulset").versioned(false).build())
            .build(),
        KubernetesResource.builder()
            .spec("Spec")
            .resourceId(KubernetesResourceId.builder().kind("DaemonSet").name("test-demonset").versioned(false).build())
            .build(),
        KubernetesResource.builder()
            .spec("Spec")
            .resourceId(KubernetesResourceId.builder().kind("Job").name("test-job").versioned(false).build())
            .build());

    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    config.setResources(allWorkloads);

    doReturn("").when(k8sTaskHelperBase).getResourcesInTableFormat(anyList());
    doNothing().when(k8sTaskHelperBase).checkSteadyStateCondition(anyList());

    boolean result = baseHandler.prepare(logCallback, false, config);
    assertThat(result).isTrue();
    assertThat(config.getWorkloads()).isNotEmpty();
    assertThat(config.getWorkloads().size()).isEqualTo(5);
    for (KubernetesResource managedWorkloads : config.getWorkloads()) {
      assertThat(managedWorkloadsKinds).contains(managedWorkloads.getResourceId().getKind());
    }
    assertThat(config.getCustomWorkloads()).isNotEmpty();
    assertThat(config.getCustomWorkloads().size()).isEqualTo(1);
    for (KubernetesResource customWorkloads : config.getCustomWorkloads()) {
      assertThat(managedWorkloadsKinds).doesNotContain(customWorkloads.getResourceId().getKind());
    }

    verify(k8sTaskHelperBase, times(1)).checkSteadyStateCondition(eq(config.getCustomWorkloads()));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldPrepareIfNoWorkloadSpecified() {
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();
    config.setResources(Collections.emptyList());

    doReturn("").when(k8sTaskHelperBase).getResourcesInTableFormat(anyList());

    boolean result = baseHandler.prepare(logCallback, false, config);
    assertThat(result).isTrue();
    assertThat(config.getWorkloads()).isEmpty();
    assertThat(config.getCustomWorkloads()).isEmpty();
    verify(k8sTaskHelperBase, times(0)).checkSteadyStateCondition(eq(config.getCustomWorkloads()));
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldFailPrepareIfNoWorkloadSpecified() {
    K8sApplyHandlerConfig config = new K8sApplyHandlerConfig();

    boolean result = baseHandler.prepare(logCallback, false, config);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void shouldDescribeManifestAsPartOfWrapUp() throws Exception {
    Kubectl client = Kubectl.client("kubtectl", "config");

    baseHandler.wrapUp(delegateTaskParams, logCallback, client);
    verify(k8sTaskHelperBase, times(1)).describe(client, delegateTaskParams, logCallback);
  }
}
