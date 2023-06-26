package io.harness.cdng.manifest.steps.task;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.delegate.K8sManifestDelegateMapper;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.delegate.AccountId;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.TaskType;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class HelmChartManifestTaskHandlerTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private K8sManifestDelegateMapper manifestDelegateMapper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;

  @InjectMocks private HelmChartManifestTaskHandler helmChartManifestTaskHandler;

  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();

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