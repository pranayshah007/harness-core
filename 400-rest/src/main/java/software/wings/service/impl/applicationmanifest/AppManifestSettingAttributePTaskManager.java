/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.applicationmanifest;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.logcontext.SettingAttributeLogContext;
import software.wings.service.impl.SettingAttributeObserver;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class AppManifestSettingAttributePTaskManager implements SettingAttributeObserver {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private AppManifestPTaskHelper appManifestPTaskHelper;
  @Inject private HelmChartService helmChartService;

  private final EnumSet<SettingVariableTypes> helmSettingAttributeTypes =
      EnumSet.of(SettingVariableTypes.HTTP_HELM_REPO, SettingVariableTypes.AMAZON_S3_HELM_REPO,
          SettingVariableTypes.GCS_HELM_REPO, SettingVariableTypes.OCI_HELM_REPO);

  @Override
  public void onSaved(SettingAttribute settingAttribute) {
    // Nothing to do on save as no application manifests are linked yet
  }

  @Override
  public void onUpdated(SettingAttribute prevSettingAttribute, SettingAttribute currSettingAttribute) {
    if (currSettingAttribute.getValue() == null
        || !helmSettingAttributeTypes.contains(currSettingAttribute.getValue().getSettingType())) {
      return;
    }

    if (!featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, currSettingAttribute.getAccountId())) {
      return;
    }

    if (!connectorValueChanged(prevSettingAttribute, currSettingAttribute)
        && !credentialsChanged(prevSettingAttribute, currSettingAttribute)) {
      return;
    }

    try (AutoLogContext ignore1 = new AccountLogContext(currSettingAttribute.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new SettingAttributeLogContext(currSettingAttribute.getUuid(), OVERRIDE_ERROR)) {
      List<ApplicationManifest> applicationManifests = applicationManifestService.listHelmChartSourceBySettingId(
          currSettingAttribute.getAccountId(), currSettingAttribute.getUuid());

      applicationManifests.forEach(applicationManifest -> {
        if (connectorValueChanged(prevSettingAttribute, currSettingAttribute)) {
          helmChartService.deleteByAppManifest(applicationManifest.getAppId(), applicationManifest.getUuid());
        }
        if (applicationManifest.getPerpetualTaskId() != null) {
          appManifestPTaskHelper.resetPerpetualTask(applicationManifest);
        }
      });
    }
  }

  private boolean credentialsChanged(SettingAttribute prevSettingAttribute, SettingAttribute currSettingAttribute) {
    if (currSettingAttribute.getValue() instanceof HttpHelmRepoConfig) {
      HttpHelmRepoConfig currHttpHelmRepoConfig = (HttpHelmRepoConfig) currSettingAttribute.getValue();
      HttpHelmRepoConfig prevHttpHelmRepoConfig = (HttpHelmRepoConfig) prevSettingAttribute.getValue();
      return !StringUtils.equals(prevHttpHelmRepoConfig.getUsername(), currHttpHelmRepoConfig.getUsername())
          || !Arrays.equals(prevHttpHelmRepoConfig.getPassword(), currHttpHelmRepoConfig.getPassword());
    }
    return false;
  }

  private boolean connectorValueChanged(SettingAttribute prevSettingAttribute, SettingAttribute currSettingAttribute) {
    if (currSettingAttribute.getValue() instanceof AmazonS3HelmRepoConfig) {
      AmazonS3HelmRepoConfig currAmazonS3HelmRepoConfig = (AmazonS3HelmRepoConfig) currSettingAttribute.getValue();
      AmazonS3HelmRepoConfig prevAmazonS3HelmRepoConfig = (AmazonS3HelmRepoConfig) prevSettingAttribute.getValue();
      return !StringUtils.equals(
                 prevAmazonS3HelmRepoConfig.getConnectorId(), currAmazonS3HelmRepoConfig.getConnectorId())
          || !StringUtils.equals(prevAmazonS3HelmRepoConfig.getBucketName(), currAmazonS3HelmRepoConfig.getBucketName())
          || !StringUtils.equals(prevAmazonS3HelmRepoConfig.getRegion(), currAmazonS3HelmRepoConfig.getRegion());
    } else if (currSettingAttribute.getValue() instanceof GCSHelmRepoConfig) {
      GCSHelmRepoConfig currGcsHelmRepoConfig = (GCSHelmRepoConfig) currSettingAttribute.getValue();
      GCSHelmRepoConfig prevGcsHelmRepoConfig = (GCSHelmRepoConfig) prevSettingAttribute.getValue();
      return !StringUtils.equals(prevGcsHelmRepoConfig.getConnectorId(), currGcsHelmRepoConfig.getConnectorId())
          || !StringUtils.equals(prevGcsHelmRepoConfig.getBucketName(), currGcsHelmRepoConfig.getBucketName());
    } else if (currSettingAttribute.getValue() instanceof HttpHelmRepoConfig) {
      HttpHelmRepoConfig currHttpHelmRepoConfig = (HttpHelmRepoConfig) currSettingAttribute.getValue();
      HttpHelmRepoConfig prevHttpHelmRepoConfig = (HttpHelmRepoConfig) prevSettingAttribute.getValue();
      return !StringUtils.equals(prevHttpHelmRepoConfig.getChartRepoUrl(), currHttpHelmRepoConfig.getChartRepoUrl());
    }
    return false;
  }

  @Override
  public void onDeleted(SettingAttribute settingAttribute) {
    // Nothing to do on delete as error message is thrown if app manifests are being referenced
  }
}
