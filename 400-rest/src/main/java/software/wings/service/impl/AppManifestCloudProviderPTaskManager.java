/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.beans.FeatureName;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.beans.AwsConfig;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.logcontext.SettingAttributeLogContext;
import software.wings.service.impl.applicationmanifest.AppManifestPTaskHelper;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class AppManifestCloudProviderPTaskManager implements CloudProviderObserver {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private AppManifestPTaskHelper appManifestPTaskHelper;
  @Inject private HelmChartService helmChartService;
  @Inject private SettingsService settingAttributeService;

  private final EnumSet<SettingVariableTypes> helmCloudProviders =
      EnumSet.of(SettingVariableTypes.AWS, SettingVariableTypes.GCP);

  @Override
  public void onSaved(SettingAttribute settingAttribute) {}

  @Override
  public void onUpdated(SettingAttribute prevSettingAttribute, SettingAttribute currSettingAttribute) {
    if (currSettingAttribute.getValue() == null
        || !helmCloudProviders.contains(currSettingAttribute.getValue().getSettingType())) {
      return;
    }

    if (!featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, currSettingAttribute.getAccountId())) {
      return;
    }

    if (!credentialsChanged(prevSettingAttribute, currSettingAttribute)) {
      return;
    }

    try (AutoLogContext ignore1 = new AccountLogContext(currSettingAttribute.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new SettingAttributeLogContext(currSettingAttribute.getUuid(), OVERRIDE_ERROR)) {
      List<SettingAttribute> settingAttributes = settingAttributeService.getSettingAttributeByReferencedConnector(
          currSettingAttribute.getAccountId(), currSettingAttribute.getUuid());

      if (isEmpty(settingAttributes)) {
        return;
      }

      for (SettingAttribute settingAttribute : settingAttributes) {
        List<ApplicationManifest> applicationManifests = applicationManifestService.listHelmChartSourceBySettingId(
            settingAttribute.getAccountId(), settingAttribute.getUuid());

        applicationManifests.forEach(applicationManifest -> {
          if (applicationManifest.getPerpetualTaskId() != null) {
            appManifestPTaskHelper.resetPerpetualTask(applicationManifest);
          }
        });
      }
    }
  }

  private boolean credentialsChanged(SettingAttribute prevSettingAttribute, SettingAttribute currSettingAttribute) {
    if (currSettingAttribute.getValue() instanceof AwsConfig) {
      AwsConfig currAwsConfig = (AwsConfig) currSettingAttribute.getValue();
      AwsConfig prevAwsConfig = (AwsConfig) prevSettingAttribute.getValue();
      return !StringUtils.equals(prevAwsConfig.getEncryptedSecretKey(), currAwsConfig.getEncryptedSecretKey())
          || !Arrays.equals(prevAwsConfig.getAccessKey(), currAwsConfig.getAccessKey());
    } else if (currSettingAttribute.getValue() instanceof GcpConfig) {
      GcpConfig currGcpConfig = (GcpConfig) currSettingAttribute.getValue();
      GcpConfig prevGcpConfig = (GcpConfig) prevSettingAttribute.getValue();
      return !Arrays.equals(
          prevGcpConfig.getServiceAccountKeyFileContent(), currGcpConfig.getServiceAccountKeyFileContent());
    }
    return false;
  }

  @Override
  public void onDeleted(SettingAttribute settingAttribute) {}
}
