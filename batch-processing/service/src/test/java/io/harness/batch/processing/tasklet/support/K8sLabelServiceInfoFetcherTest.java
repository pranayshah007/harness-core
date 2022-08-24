/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.batch.processing.writer.constants.K8sCCMConstants.HELM_RELEASE_NAME;
import static io.harness.rule.OwnerRule.AVMOHAN;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.writer.constants.K8sCCMConstants;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.rule.Owner;

import software.wings.api.DeploymentSummary;
import software.wings.api.K8sDeploymentInfo;
import software.wings.beans.container.Label;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

public class K8sLabelServiceInfoFetcherTest extends CategoryTest {
  private static final String ACCOUNT_ID = "fcf53242-4a9d-4b8c-8497-5ba7360569d9";
  private K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;
  private CloudToHarnessMappingService cloudToHarnessMappingService;

  @Before
  public void setUp() throws Exception {
    cloudToHarnessMappingService = mock(CloudToHarnessMappingService.class);
    k8sLabelServiceInfoFetcher = new K8sLabelServiceInfoFetcher(cloudToHarnessMappingService);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldReturnEmptyOptionalWhenNoReleaseNameLabel() throws Exception {
    assertThat(
        k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(ACCOUNT_ID, ImmutableMap.of("key1", "value1")))
        .isNotPresent();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldReturnEmptyOptionalWhenCloudToHarnessMappingNotFound() throws Exception {
    String relName = "115856c0-8bd5-4d5c-901b-a9abe538f6c4";
    ArgumentCaptor<DeploymentSummary> captor = ArgumentCaptor.forClass(DeploymentSummary.class);
    when(cloudToHarnessMappingService.getHarnessServiceInfo(captor.capture())).thenReturn(Optional.empty());
    assertThat(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(
                   ACCOUNT_ID, ImmutableMap.of("key1", "value1", K8sCCMConstants.RELEASE_NAME, relName)))
        .isNotPresent();
    assertThat(captor.getValue()).satisfies(deploymentSummary -> {
      assertThat(deploymentSummary.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(deploymentSummary.getK8sDeploymentKey().getReleaseName()).isEqualTo(relName);
      assertThat(deploymentSummary.getDeploymentInfo())
          .isInstanceOfSatisfying(K8sDeploymentInfo.class,
              k8sDeploymentInfo -> { assertThat(k8sDeploymentInfo.getReleaseName()).isEqualTo(relName); });
    });
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldReturnHarnessSvcInfoWhenLabelPresentAndMappingFound() throws Exception {
    String relName = "115856c0-8bd5-4d5c-901b-a9abe538f6c4";
    ArgumentCaptor<DeploymentSummary> captor = ArgumentCaptor.forClass(DeploymentSummary.class);
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        "svc-id", "app-id", "cloud-provider-id", "env-id", "infra-mapping-id", "deployment-summary-id");
    when(cloudToHarnessMappingService.getHarnessServiceInfo(captor.capture()))
        .thenReturn(Optional.of(harnessServiceInfo));
    assertThat(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(
                   ACCOUNT_ID, ImmutableMap.of("key1", "value1", K8sCCMConstants.RELEASE_NAME, relName)))
        .isPresent()
        .hasValue(harnessServiceInfo);
    assertThat(captor.getValue()).satisfies(deploymentSummary -> {
      assertThat(deploymentSummary.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(deploymentSummary.getK8sDeploymentKey().getReleaseName()).isEqualTo(relName);
      assertThat(deploymentSummary.getDeploymentInfo())
          .isInstanceOfSatisfying(K8sDeploymentInfo.class,
              k8sDeploymentInfo -> { assertThat(k8sDeploymentInfo.getReleaseName()).isEqualTo(relName); });
    });
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnHarnessSvcInfoWhenHelmLabelPresentAndMappingFound() throws Exception {
    String relName = "115856c0-8bd5-4d5c-901b-a9abe538f6c4";
    ArgumentCaptor<DeploymentSummary> captor = ArgumentCaptor.forClass(DeploymentSummary.class);
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        "svc-id", "app-id", "cloud-provider-id", "env-id", "infra-mapping-id", "deployment-summary-id");
    when(cloudToHarnessMappingService.getHarnessServiceInfo(captor.capture()))
        .thenReturn(Optional.of(harnessServiceInfo));
    assertThat(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(
                   ACCOUNT_ID, ImmutableMap.of("key1", "value1", K8sCCMConstants.HELM_RELEASE_NAME, relName)))
        .isPresent()
        .hasValue(harnessServiceInfo);
    Label label = Label.Builder.aLabel().withName(HELM_RELEASE_NAME).withValue(relName).build();
    assertThat(captor.getValue()).satisfies(deploymentSummary -> {
      assertThat(deploymentSummary.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(deploymentSummary.getContainerDeploymentKey().getLabels()).isEqualTo(Arrays.asList(label));
    });
  }
}
