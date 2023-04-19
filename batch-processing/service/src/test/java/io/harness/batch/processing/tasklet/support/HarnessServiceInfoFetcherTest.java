/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.support;

import static io.harness.batch.processing.writer.constants.K8sCCMConstants.K8SV1_RELEASE_NAME;
import static io.harness.batch.processing.writer.constants.K8sCCMConstants.RELEASE_NAME;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HarnessServiceInfoFetcherTest extends CategoryTest {
  private static final String POD_NAME = "podName";
  private static final String NAMESPACE = "namespace";
  private static final String SETTING_ID = "settingId";
  private static final String ACCOUNT_ID = "fcf53242-4a9d-4b8c-8497-5ba7360569d9";
  private HarnessServiceInfoFetcher harnessServiceInfoFetcher;
  private K8sLabelServiceInfoFetcher k8sLabelServiceInfoFetcher;
  private CloudToHarnessMappingService cloudToHarnessMappingService;
  private FeatureFlagService featureFlagService;

  @Before
  public void setUp() throws Exception {
    cloudToHarnessMappingService = mock(CloudToHarnessMappingService.class);
    k8sLabelServiceInfoFetcher = mock(K8sLabelServiceInfoFetcher.class);
    featureFlagService = mock(FeatureFlagService.class);
    harnessServiceInfoFetcher =
        new HarnessServiceInfoFetcher(k8sLabelServiceInfoFetcher, cloudToHarnessMappingService, featureFlagService);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnHarnessSvcInfoWhenK8sV1LabelPresentAndMappingFound() throws Exception {
    ImmutableMap<String, String> labels = ImmutableMap.of(K8SV1_RELEASE_NAME, "value1");
    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(ACCOUNT_ID, labels))
        .thenReturn(Optional.ofNullable(null));
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        "svc-id", "app-id", "cloud-provider-id", "env-id", "infra-mapping-id", "deployment-summary-id");
    when(cloudToHarnessMappingService.getHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME))
        .thenReturn(Optional.of(harnessServiceInfo));
    assertThat(harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME, labels))
        .isPresent()
        .hasValue(harnessServiceInfo);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnHarnessSvcInfoWhenLabelPresentAndMappingFound() throws Exception {
    ImmutableMap<String, String> labels = ImmutableMap.of(RELEASE_NAME, "value1");
    HarnessServiceInfo harnessServiceInfo = new HarnessServiceInfo(
        "svc-id", "app-id", "cloud-provider-id", "env-id", "infra-mapping-id", "deployment-summary-id");
    when(featureFlagService.isEnabled(any(), anyString())).thenReturn(false);
    when(cloudToHarnessMappingService.getHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME))
        .thenReturn(Optional.empty());
    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(ACCOUNT_ID, labels))
        .thenReturn(Optional.of(harnessServiceInfo));
    assertThat(harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME, labels))
        .isPresent()
        .hasValue(harnessServiceInfo);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnEmptyOptionalWhenNoReleaseNameLabel() throws Exception {
    ImmutableMap<String, String> labels = ImmutableMap.of("key1", "value1");

    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(ACCOUNT_ID, labels)).thenAnswer(invocation -> {
      throw new Exception();
    });
    assertThat(harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME, labels))
        .isNotPresent();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldCacheCloudToHarnessMappingServiceNotFound() {
    ImmutableMap<String, String> labels = ImmutableMap.of(K8SV1_RELEASE_NAME, "value1");
    when(k8sLabelServiceInfoFetcher.fetchHarnessServiceInfoFromCache(any(), any())).thenReturn(Optional.empty());
    when(cloudToHarnessMappingService.getHarnessServiceInfo(any(), any(), any(), any())).thenReturn(Optional.empty());

    Optional<HarnessServiceInfo> result =
        harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME, labels);
    assertThat(result.isPresent()).isFalse();

    Optional<HarnessServiceInfo> resultRetry =
        harnessServiceInfoFetcher.fetchHarnessServiceInfo(ACCOUNT_ID, SETTING_ID, NAMESPACE, POD_NAME, labels);
    assertThat(resultRetry.isPresent()).isFalse();

    verify(cloudToHarnessMappingService, times(1)).getHarnessServiceInfo(any(), any(), any(), any());
  }
}
