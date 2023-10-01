/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps.task;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
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
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class HelmChartManifestTaskHandler implements ManifestTaskHandler {
  public static final long DEFAULT_FETCH_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();

  @Inject private K8sManifestDelegateMapper manifestDelegateMapper;

  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Inject private CDFeatureFlagHelper featureFlagHelperService;

  @Override
  public boolean isSupported(FetchManifestTaskContext context) {
    if (!ManifestType.HelmChart.equals(context.getType())) {
      return false;
    }

    final String accountId = AmbianceUtils.getAccountId(context.getAmbiance());

    if (!featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_HELM_FETCH_CHART_METADATA_NG)) {
      return false;
    }

    HelmChartManifestOutcome helmChartManifest = (HelmChartManifestOutcome) context.getManifestOutcome();
    if (!getBooleanParameterFieldValue(helmChartManifest.getFetchHelmChartMetadata())) {
      return false;
    }

    List<String> unresolvedExpressions = findUnresolvedExpressions(helmChartManifest);
    if (isNotEmpty(unresolvedExpressions)) {
      String unresolvedExpressionsString = String.join(", ", unresolvedExpressions);
      context.warn(String.format("Manifest [%s] contains unresolved expressions: [%s]",
          helmChartManifest.getIdentifier(), unresolvedExpressionsString));
      context.warn("Fetching helm chart manifest is not possible if it is dependent on runtime expressions");
      return false;
    }

    final io.harness.delegate.TaskType taskType =
        io.harness.delegate.TaskType.newBuilder().setType(TaskType.HELM_FETCH_CHART_MANIFEST_TASK.name()).build();

    if (!delegateGrpcClientWrapper.isTaskTypeSupported(AccountId.newBuilder().setId(accountId).build(), taskType)) {
      context.warn(String.format(
          "Not all delegates support task [%s]. To use fetchHelmChartMetadata [manifest: %s] option all delegates should be up to date",
          TaskType.HELM_FETCH_CHART_MANIFEST_TASK.getDisplayName(), helmChartManifest.getIdentifier()));
      return false;
    }

    return true;
  }

  @Override
  public Optional<TaskData> createTaskData(FetchManifestTaskContext context) {
    ManifestOutcome manifest = context.getManifestOutcome();
    if (!(manifest instanceof HelmChartManifestOutcome)) {
      log.warn("Incorrect type used: {}, expected: {}",
          manifest != null ? manifest.getClass().getSimpleName() : "<null>",
          HelmChartManifestOutcome.class.getSimpleName());
      return Optional.empty();
    }

    ManifestDelegateConfig manifestDelegateConfig =
        manifestDelegateMapper.getManifestDelegateConfig(manifest, context.getAmbiance());
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
                               context.getAmbiance(), (HelmChartManifestDelegateConfig) manifestDelegateConfig)})
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

  private List<String> findUnresolvedExpressions(HelmChartManifestOutcome manifestOutcome) {
    List<String> unresolvedFields = new ArrayList<>();
    ExpressionEvaluatorUtils.updateExpressions(manifestOutcome, value -> {
      if (EngineExpressionEvaluator.hasExpressions(value)) {
        unresolvedFields.add(value);
      }
      return value;
    });

    return unresolvedFields;
  }
}
