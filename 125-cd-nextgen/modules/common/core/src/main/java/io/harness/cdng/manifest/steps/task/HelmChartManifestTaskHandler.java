package io.harness.cdng.manifest.steps.task;

import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.delegate.K8sManifestDelegateMapper;
import io.harness.cdng.manifest.outcome.HelmChartOutcome;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.helm.request.HelmFetchChartManifestTaskParameters;
import io.harness.delegate.task.helm.response.HelmFetchChartManifestResponse;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.tasks.ResponseData;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class HelmChartManifestTaskHandler implements ManifestTaskHandler {
  public static final long DEFAULT_FETCH_TIMEOUT_MILLIS = Duration.ofMinutes(5).toMillis();

  @Inject private K8sManifestDelegateMapper manifestDelegateMapper;

  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject private NGFeatureFlagHelperService featureFlagHelperService;

  @Override
  public boolean isSupported(Ambiance ambiance, ManifestOutcome manifest) {
    if (!ManifestType.HelmChart.equals(manifest.getType())) {
      return false;
    }

    final String accountId = AmbianceUtils.getAccountId(ambiance);

    if (!featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_HELM_FETCH_CHART_METADATA_NG)) {
      return false;
    }

    HelmChartManifestOutcome helmChartManifest = (HelmChartManifestOutcome) manifest;
    if (!getBooleanParameterFieldValue(helmChartManifest.getFetchHelmChartMetadata())) {
      return false;
    }

    final io.harness.delegate.TaskType taskType =
        io.harness.delegate.TaskType.newBuilder().setType(TaskType.HELM_FETCH_CHART_MANIFEST_TASK.name()).build();

    return delegateGrpcClientWrapper.isTaskTypeSupported(AccountId.newBuilder().setId(accountId).build(), taskType);
  }

  @Override
  public Optional<TaskData> createTaskData(Ambiance ambiance, ManifestOutcome manifest) {
    if (!(manifest instanceof HelmChartManifestOutcome)) {
      log.warn("Incorrect type used: {}, expected: {}", manifest != null ? manifest.getClass().getSimpleName() : "<null>",
              HelmChartManifestOutcome.class.getSimpleName());
      return Optional.empty();
    }

    ManifestDelegateConfig manifestDelegateConfig =
        manifestDelegateMapper.getManifestDelegateConfig(manifest, ambiance);
    if (!(manifestDelegateConfig instanceof HelmChartManifestDelegateConfig)) {
      log.warn("Incorrect manifest delegate config type: {}, expected: {}",
              manifestDelegateConfig != null ? manifestDelegateConfig.getClass().getSimpleName() : "<null>",
              HelmChartManifestDelegateConfig.class.getSimpleName());
      return Optional.empty();
    }

    return Optional.of(TaskData.builder()
                           .async(true)
                           .taskType(TaskType.HELM_FETCH_CHART_MANIFEST_TASK.name())
                           .parameters(new Object[] {createTaskParameters(
                               ambiance, (HelmChartManifestDelegateConfig) manifestDelegateConfig)})
                           .build());
  }

  @Override
  public Optional<ManifestOutcome> updateManifestOutcome(ResponseData response, ManifestOutcome manifestOutcome) {
    if (!(response instanceof HelmFetchChartManifestResponse)) {
      log.warn("Received invalid task response type {}, expected: {}",
          response != null ? response.getClass().getSimpleName() : "<null>",
          HelmFetchChartManifestResponse.class.getSimpleName());
      return Optional.empty();
    }

    if (!(manifestOutcome instanceof HelmChartManifestOutcome)) {
      log.warn("Incorrect manifest outcome type {}, expected: {}",
          manifestOutcome != null ? manifestOutcome.getClass().getSimpleName() : "<null>",
          HelmChartManifestOutcome.class.getSimpleName());
      return Optional.empty();
    }

    HelmFetchChartManifestResponse fetchResponse = (HelmFetchChartManifestResponse) response;
    HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;

    if (fetchResponse.getHelmChartManifest() == null) {
      log.warn("Received null helm chart manifest from task response");
      return Optional.empty();
    }

    return Optional.of(
        helmChartManifestOutcome.toBuilder().helm(HelmChartOutcome.from(fetchResponse.getHelmChartManifest())).build());
  }

  private HelmFetchChartManifestTaskParameters createTaskParameters(
      Ambiance ambiance, HelmChartManifestDelegateConfig manifestDelegateConfig) {
    return HelmFetchChartManifestTaskParameters.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .helmChartConfig(manifestDelegateConfig)
        .timeoutInMillis(DEFAULT_FETCH_TIMEOUT_MILLIS)
        .build();
  }
}
