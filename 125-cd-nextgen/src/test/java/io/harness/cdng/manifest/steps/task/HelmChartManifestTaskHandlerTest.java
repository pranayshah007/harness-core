/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps.task;

import static io.harness.cdng.manifest.steps.task.HelmChartManifestTaskHandler.DEFAULT_FETCH_TIMEOUT_MILLIS;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.delegate.K8sManifestDelegateMapper;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.helm.request.HelmFetchChartManifestTaskParameters;
import io.harness.delegate.task.helm.response.HelmChartManifest;
import io.harness.delegate.task.helm.response.HelmFetchChartManifestResponse;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartManifestTaskHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private K8sManifestDelegateMapper manifestDelegateMapper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private CDFeatureFlagHelper featureFlagHelperService;

  @InjectMocks private HelmChartManifestTaskHandler helmChartManifestTaskHandler;

  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", ACCOUNT_ID).build();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupported() {
    testIsSupported(ParameterField.createValueField(true), true, true, true);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedTaskNotSupported() {
    testIsSupported(ParameterField.createValueField(true), true, false, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedFFNotEnabled() {
    testIsSupported(ParameterField.createValueField(true), false, true, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedNotEnabled() {
    testIsSupported(ParameterField.createValueField(false), true, true, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedNullValue() {
    testIsSupported(null, true, true, false);
    testIsSupported(ParameterField.createValueField(null), true, true, false);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedFFK8sManifest() {
    final io.harness.delegate.TaskType expectedTaskType =
        io.harness.delegate.TaskType.newBuilder().setType(TaskType.HELM_FETCH_CHART_MANIFEST_TASK.name()).build();
    final AccountId expectedAccountId = AccountId.newBuilder().setId(ACCOUNT_ID).build();
    final K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();

    doReturn(true).when(featureFlagHelperService).isEnabled(ACCOUNT_ID, FeatureName.CDS_HELM_FETCH_CHART_METADATA_NG);

    doReturn(true).when(delegateGrpcClientWrapper).isTaskTypeSupported(expectedAccountId, expectedTaskType);

    assertThat(helmChartManifestTaskHandler.isSupported(ambiance, manifestOutcome)).isFalse();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTaskData() {
    final HelmChartManifestOutcome manifestOutcome = HelmChartManifestOutcome.builder().build();
    final HelmChartManifestDelegateConfig manifestConfig = HelmChartManifestDelegateConfig.builder().build();

    doReturn(manifestConfig).when(manifestDelegateMapper).getManifestDelegateConfig(manifestOutcome, ambiance);

    Optional<TaskData> result = helmChartManifestTaskHandler.createTaskData(ambiance, manifestOutcome);

    assertThat(result).isNotEmpty();

    final TaskData taskData = result.get();
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.HELM_FETCH_CHART_MANIFEST_TASK.name());
    assertThat(taskData.getParameters()).isNotEmpty();
    assertThat(taskData.getParameters()).hasSize(1);

    final Object taskParameters = taskData.getParameters()[0];
    assertThat(taskParameters).isInstanceOf(HelmFetchChartManifestTaskParameters.class);

    final HelmFetchChartManifestTaskParameters helmFetchTaskParameters =
        (HelmFetchChartManifestTaskParameters) taskParameters;
    assertThat(helmFetchTaskParameters.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(helmFetchTaskParameters.getHelmChartConfig()).isEqualTo(manifestConfig);
    assertThat(helmFetchTaskParameters.getTimeoutInMillis()).isEqualTo(DEFAULT_FETCH_TIMEOUT_MILLIS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTaskDataInvalidManifest() {
    final K8sManifestOutcome k8sManifestOutcome = K8sManifestOutcome.builder().build();
    Optional<TaskData> result = helmChartManifestTaskHandler.createTaskData(ambiance, k8sManifestOutcome);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTaskDataIncorrectManifestDelegateType() {
    final HelmChartManifestOutcome manifestOutcome = HelmChartManifestOutcome.builder().build();
    final K8sManifestDelegateConfig manifestDelegateConfig = K8sManifestDelegateConfig.builder().build();

    doReturn(manifestDelegateConfig).when(manifestDelegateMapper).getManifestDelegateConfig(manifestOutcome, ambiance);

    Optional<TaskData> result = helmChartManifestTaskHandler.createTaskData(ambiance, manifestOutcome);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateManifestOutcome() {
    final Map<String, String> metadata = ImmutableMap.of("bucket", "test", "url", "https://test.io");
    final HelmFetchChartManifestResponse response = HelmFetchChartManifestResponse.builder()
                                                        .helmChartManifest(HelmChartManifest.builder()
                                                                               .type("type")
                                                                               .description("Test chart")
                                                                               .appVersion("1.0.0")
                                                                               .apiVersion("v2")
                                                                               .kubeVersion(">=1.19.1")
                                                                               .version("1.2.3")
                                                                               .name("chart")
                                                                               .metadata(metadata)
                                                                               .build())
                                                        .build();

    final HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder().chartName(ParameterField.createValueField("chart")).build();

    Optional<ManifestOutcome> result = helmChartManifestTaskHandler.updateManifestOutcome(response, manifestOutcome);
    assertThat(result).isNotEmpty();

    ManifestOutcome resultManifest = result.get();
    assertThat(resultManifest).isInstanceOf(HelmChartManifestOutcome.class);

    HelmChartManifestOutcome updatedManifest = (HelmChartManifestOutcome) resultManifest;
    assertThat(updatedManifest.getChartName().getValue()).isEqualTo("chart");
    assertThat(updatedManifest.getHelm().getName()).isEqualTo("chart");
    assertThat(updatedManifest.getHelm().getDescription()).isEqualTo("Test chart");
    assertThat(updatedManifest.getHelm().getAppVersion()).isEqualTo("1.0.0");
    assertThat(updatedManifest.getHelm().getApiVersion()).isEqualTo("v2");
    assertThat(updatedManifest.getHelm().getVersion()).isEqualTo("1.2.3");
    assertThat(updatedManifest.getHelm().getKubeVersion()).isEqualTo(">=1.19.1");
    assertThat(updatedManifest.getHelm().getMetadata()).isEqualTo(metadata);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateManifestOutcomeInvalidResponseType() {
    final ResponseData response = mock(ResponseData.class);
    final HelmChartManifestOutcome manifestOutcome = HelmChartManifestOutcome.builder().build();

    Optional<ManifestOutcome> result = helmChartManifestTaskHandler.updateManifestOutcome(response, manifestOutcome);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateManifestOutcomeResponseWithNullManifest() {
    final HelmFetchChartManifestResponse response = HelmFetchChartManifestResponse.builder().build();
    final HelmChartManifestOutcome manifestOutcome = HelmChartManifestOutcome.builder().build();

    Optional<ManifestOutcome> result = helmChartManifestTaskHandler.updateManifestOutcome(response, manifestOutcome);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateManifestOutcomeNullResponse() {
    final HelmChartManifestOutcome manifestOutcome = HelmChartManifestOutcome.builder().build();

    Optional<ManifestOutcome> result = helmChartManifestTaskHandler.updateManifestOutcome(null, manifestOutcome);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateManifestOutcomeNullManifest() {
    final HelmFetchChartManifestResponse response =
        HelmFetchChartManifestResponse.builder().helmChartManifest(HelmChartManifest.builder().build()).build();

    Optional<ManifestOutcome> result = helmChartManifestTaskHandler.updateManifestOutcome(response, null);
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateManifestOutcomeInvalidManifest() {
    final HelmFetchChartManifestResponse response =
        HelmFetchChartManifestResponse.builder().helmChartManifest(HelmChartManifest.builder().build()).build();
    final K8sManifestOutcome manifestOutcome = K8sManifestOutcome.builder().build();

    Optional<ManifestOutcome> result = helmChartManifestTaskHandler.updateManifestOutcome(response, manifestOutcome);
    assertThat(result).isEmpty();
  }

  private void testIsSupported(
      ParameterField<Boolean> fetchHelmChart, boolean ffEnabled, boolean taskSupported, boolean expectedResult) {
    final io.harness.delegate.TaskType expectedTaskType =
        io.harness.delegate.TaskType.newBuilder().setType(TaskType.HELM_FETCH_CHART_MANIFEST_TASK.name()).build();
    final AccountId expectedAccountId = AccountId.newBuilder().setId(ACCOUNT_ID).build();
    final HelmChartManifestOutcome manifestOutcome =
        HelmChartManifestOutcome.builder().fetchHelmChartMetadata(fetchHelmChart).build();

    doReturn(ffEnabled)
        .when(featureFlagHelperService)
        .isEnabled(ACCOUNT_ID, FeatureName.CDS_HELM_FETCH_CHART_METADATA_NG);

    doReturn(taskSupported).when(delegateGrpcClientWrapper).isTaskTypeSupported(expectedAccountId, expectedTaskType);

    assertThat(helmChartManifestTaskHandler.isSupported(ambiance, manifestOutcome)).isEqualTo(expectedResult);
  }
}