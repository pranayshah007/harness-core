/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm.steadystate;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.HelmClientException;
import io.harness.helm.HelmClient;
import io.harness.helm.HelmClientImpl.HelmCliResponse;
import io.harness.helm.HelmClientUtils;
import io.harness.helm.HelmCommandData;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class HelmSteadyStateServiceTest extends CategoryTest {
  private static final String RES_BASE_PATH = "k8s/helm";
  private static final String RES_HELM_MANIFEST_DEPLOYMENT = "helm-manifest-deployment.yaml";
  private static final String RES_HELM_MANIFEST_ALL = "helm-manifest-all.yaml";
  private static final String NAMESPACE = "namespace-1";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private HelmClient helmClient;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private LogCallback logCallback;

  @InjectMocks private HelmSteadyStateService helmSteadyStateService;

  private HelmCommandData helmCommandData = null;

  @Before
  public void setup() {
    helmCommandData =
        HelmCommandData.builder().releaseName("release-1").namespace(NAMESPACE).logCallback(logCallback).build();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadManifestFromHelmRelease() {
    final HelmCliResponse response = HelmCliResponse.builder()
                                         .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                         .output(readResource(RES_HELM_MANIFEST_DEPLOYMENT))
                                         .build();

    doReturn(response).when(helmClient).getManifest(helmCommandData, NAMESPACE);

    List<KubernetesResource> resourceList = helmSteadyStateService.readManifestFromHelmRelease(helmCommandData);

    assertThat(resourceList.stream().map(KubernetesResource::getResourceId).map(KubernetesResourceId::kindNameRef))
        .containsExactlyInAnyOrder("Service/my-hello", "Deployment/my-hello");
    verify(k8sTaskHelperBase).setNamespaceToKubernetesResourcesIfRequired(resourceList, NAMESPACE);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testReadManifestFromHelmReleaseFailedResponse() {
    final HelmCliResponse response =
        HelmCliResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();

    doReturn(response).when(helmClient).getManifest(helmCommandData, NAMESPACE);

    // Not a priority test and can be removed if that is the case. Logic is added just because of possibility
    // to have a different command execution status but is expected that helm client will return only successful
    // case otherwise will throw an exception
    assertThatThrownBy(() -> helmSteadyStateService.readManifestFromHelmRelease(helmCommandData))
        .isInstanceOf(HelmClientException.class);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFindEligibleWorkloadIds() {
    List<KubernetesResource> resources =
        HelmClientUtils.readManifestFromHelmOutput(readResource(RES_HELM_MANIFEST_ALL));

    List<KubernetesResourceId> workloadsIds = helmSteadyStateService.findEligibleWorkloadIds(resources);

    assertThat(workloadsIds.stream().map(KubernetesResourceId::kindNameRef))
        .containsExactlyInAnyOrder("Deployment/my-hello", "StatefulSet/my-hello", "Job/my-hello-job",
            "DaemonSet/my-hello-daemon-set", "DeploymentConfig/my-hello-deployment-config");
  }

  @SneakyThrows
  private static String readResource(String file) {
    ClassLoader classLoader = HelmSteadyStateServiceTest.class.getClassLoader();
    return Resources.toString(
        Objects.requireNonNull(classLoader.getResource(RES_BASE_PATH + "/" + file)), StandardCharsets.UTF_8);
  }
}