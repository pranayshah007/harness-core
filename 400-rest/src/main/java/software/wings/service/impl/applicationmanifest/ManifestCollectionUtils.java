/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.applicationmanifest;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.validation.Validator.notNullCheck;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.settings.helm.HelmRepoConfig;
import software.wings.beans.settings.helm.HelmRepoConfigValidationTaskParams;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams.HelmChartCollectionType;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams.HelmChartConfigParamsBuilder;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(_870_CG_ORCHESTRATION)
public class ManifestCollectionUtils {
  @Inject ApplicationManifestService applicationManifestService;
  @Inject AppService appService;
  @Inject SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private HelmChartService helmChartService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppManifestPTaskHelper appManifestPTaskHelper;
  @Inject private FeatureFlagService featureFlagService;

  public ManifestCollectionParams prepareCollectTaskParams(String appManifestId, String appId) {
    ApplicationManifest appManifest = applicationManifestService.getById(appId, appManifestId);
    if (appManifest == null || !Boolean.TRUE.equals(appManifest.getPollForChanges())) {
      applicationManifestService.deletePerpetualTaskByAppManifest(appService.getAccountIdByAppId(appId), appManifestId);
      throw new InvalidRequestException("Collection not configured for app manifest with id " + appManifestId);
    }
    String accountId =
        appManifest.getAccountId() != null ? appManifest.getAccountId() : appService.getAccountIdByAppId(appId);

    Service service = serviceResourceService.get(appId, appManifest.getServiceId());
    if (service == null) {
      applicationManifestService.deletePerpetualTaskByAppManifest(accountId, appManifestId);
      throw new InvalidRequestException("Service not found for the application manifest with id " + appManifestId);
    }
    HelmChartConfig helmChartConfig = appManifest.getHelmChartConfig();

    HelmChartConfigParamsBuilder helmChartConfigParamsBuilder = constructHelmChartConfigParamsBuilder(
        appId, helmChartConfig, service.getHelmVersion(), appManifest.getPerpetualTaskId(), appManifestId, accountId);

    return HelmChartCollectionParams.builder()
        .accountId(accountId)
        .appId(appId)
        .appManifestId(appManifestId)
        .serviceId(appManifest.getServiceId())
        .publishedVersions(getPublishedVersionsForAppManifest(accountId, appManifestId))
        .helmChartConfigParams(helmChartConfigParamsBuilder.build())
        .useRepoFlags(HelmVersion.isHelmV3(service.getHelmVersion()))
        .build();
  }

  public ManifestCollectionParams prepareCollectTaskParamsWithChartVersion(
      String appManifestId, String appId, HelmChartCollectionType helmChartCollectionType, String chartVersion) {
    ApplicationManifest appManifest = applicationManifestService.getById(appId, appManifestId);
    if (appManifest == null) {
      throw new InvalidRequestException("Cannot find app manifest with id " + appManifestId);
    }
    String accountId =
        appManifest.getAccountId() != null ? appManifest.getAccountId() : appService.getAccountIdByAppId(appId);

    Service service = serviceResourceService.get(appId, appManifest.getServiceId());
    if (service == null) {
      throw new InvalidRequestException("Service not found for the application manifest with id " + appManifestId);
    }
    HelmChartConfig helmChartConfig = appManifest.getHelmChartConfig();

    HelmChartConfigParamsBuilder helmChartConfigParamsBuilder = constructHelmChartConfigParamsBuilder(
        appId, helmChartConfig, service.getHelmVersion(), appManifest.getPerpetualTaskId(), appManifestId, accountId);
    // add version to config
    helmChartConfigParamsBuilder.chartVersion(chartVersion);

    return HelmChartCollectionParams.builder()
        .accountId(accountId)
        .appId(appId)
        .appManifestId(appManifestId)
        .serviceId(appManifest.getServiceId())
        .publishedVersions(getPublishedVersionsForAppManifest(accountId, appManifestId))
        .helmChartConfigParams(helmChartConfigParamsBuilder.build())
        .collectionType(helmChartCollectionType)
        .useRepoFlags(HelmVersion.isHelmV3(service.getHelmVersion()))
        .build();
  }

  private Set<String> getPublishedVersionsForAppManifest(String accountId, String appManifestId) {
    List<HelmChart> helmCharts = helmChartService.listHelmChartsForAppManifest(accountId, appManifestId);
    return helmCharts == null ? new HashSet<>()
                              : helmCharts.stream().map(HelmChart::getVersion).collect(Collectors.toSet());
  }

  private HelmChartConfigParamsBuilder constructHelmChartConfigParamsBuilder(String appId,
      HelmChartConfig helmChartConfig, HelmVersion helmVersion, String perpetualTaskId, String appManifestId,
      String accountId) {
    SettingAttribute settingAttribute = settingsService.get(helmChartConfig.getConnectorId());
    notNullCheck("Helm repo config not found with id " + helmChartConfig.getConnectorId(), settingAttribute);

    HelmRepoConfig helmRepoConfig = (HelmRepoConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptionDataDetails = secretManager.getEncryptionDetails(helmRepoConfig, appId, null);

    HelmChartConfigParamsBuilder helmChartConfigParamsBuilder =
        HelmChartConfigParams.builder()
            .chartName(helmChartConfig.getChartName())
            .chartUrl(helmChartConfig.getChartUrl())
            .basePath(helmChartConfig.getBasePath())
            .encryptedDataDetails(encryptionDataDetails)
            // TODO(achyuth): confirm if we need appManifestId here or can we use same logic as CDP
            .repoName(convertBase64UuidToCanonicalForm(appManifestId))
            .repoDisplayName(settingAttribute.getName())
            .helmVersion(helmVersion)
            .useLatestChartMuseumVersion(
                featureFlagService.isEnabled(FeatureName.USE_LATEST_CHARTMUSEUM_VERSION, accountId))
            .bypassHelmFetch(featureFlagService.isEnabled(FeatureName.BYPASS_HELM_FETCH, accountId))
            .useRepoFlags(helmVersion != HelmVersion.V2)
            .deleteRepoCacheDir(helmVersion != HelmVersion.V2)
            .useCache(helmVersion != HelmVersion.V2
                && !featureFlagService.isEnabled(FeatureName.DISABLE_HELM_REPO_YAML_CACHE, accountId))
            .helmRepoConfig(helmRepoConfig);

    if (isNotBlank(helmRepoConfig.getConnectorId())) {
      SettingAttribute connectorSettingAttribute = settingsService.get(helmRepoConfig.getConnectorId());
      if (connectorSettingAttribute != null) {
        SettingValue settingValue = connectorSettingAttribute.getValue();
        List<EncryptedDataDetail> connectorEncryptedDataDetails =
            secretManager.getEncryptionDetails((EncryptableSetting) settingValue, appId, null);

        helmChartConfigParamsBuilder.connectorConfig(settingValue)
            .connectorEncryptedDataDetails(connectorEncryptedDataDetails);
      } else {
        log.warn("Cloud provider doesn't exist with id: " + helmRepoConfig.getConnectorId());
        appManifestPTaskHelper.deletePerpetualTask(perpetualTaskId, appManifestId, accountId);
      }
    }
    return helmChartConfigParamsBuilder;
  }

  public DelegateTask buildValidationTask(String appManifestId, String appId) {
    HelmChartCollectionParams helmChartCollectionParams =
        (HelmChartCollectionParams) prepareCollectTaskParams(appManifestId, appId);

    HelmRepoConfigValidationTaskParams helmRepoConfigValidationTaskParams =
        HelmRepoConfigValidationTaskParams.builder()
            .appId(appId)
            .accountId(helmChartCollectionParams.getAccountId())
            .helmRepoConfig(helmChartCollectionParams.getHelmChartConfigParams().getHelmRepoConfig())
            .encryptedDataDetails(helmChartCollectionParams.getHelmChartConfigParams().getEncryptedDataDetails())
            .connectorConfig(helmChartCollectionParams.getHelmChartConfigParams().getConnectorConfig())
            .repoDisplayName(helmChartCollectionParams.getHelmChartConfigParams().getRepoDisplayName())
            .useLatestChartMuseumVersion(featureFlagService.isEnabled(
                FeatureName.USE_LATEST_CHARTMUSEUM_VERSION, helmChartCollectionParams.getAccountId()))
            .useOCIHelmRepo(true)
            .useNewHelmBinary(
                featureFlagService.isEnabled(FeatureName.HELM_VERSION_3_8_0, helmChartCollectionParams.getAccountId()))
            .connectorEncryptedDataDetails(
                helmChartCollectionParams.getHelmChartConfigParams().getConnectorEncryptedDataDetails())
            .useCache(helmChartCollectionParams.getHelmChartConfigParams().isUseCache())
            .build();
    return DelegateTask.builder()
        .accountId(helmChartCollectionParams.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HELM_REPO_CONFIG_VALIDATION.name())
                  .parameters(new Object[] {helmRepoConfigValidationTaskParams})
                  .timeout(TimeUnit.MINUTES.toMillis(2))
                  .build())
        .build();
  }
}
