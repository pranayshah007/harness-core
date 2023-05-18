/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.applicationmanifest;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.DockerConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AppManifestSettingAttributePTaskManagerTest extends CategoryTest {
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";
  private static final String APP_MANIFEST_ID = "APP_MANIFEST_ID";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String APP_ID = "APP_ID";
  private static final String SETTING_ID = "SETTING_ID";

  @Mock private FeatureFlagService featureFlagService;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private HelmChartService helmChartService;
  @Mock private AppManifestPTaskHelper appManifestPTaskHelper;

  @Inject @InjectMocks AppManifestSettingAttributePTaskManager manager;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testOnUpdated() {
    SettingAttribute settingAttribute = prepareSettingAttribute("https://charts.helm.sh/stable/");
    SettingAttribute currSettingAttribute = prepareSettingAttribute("https://charts2.helm.sh/stable/");
    ApplicationManifest applicationManifest = prepareAppManifest();
    applicationManifest.setPerpetualTaskId(PERPETUAL_TASK_ID);

    disableFeatureFlag();
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, never()).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);

    enableFeatureFlag();
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest, prepareAppManifest()));
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, times(2)).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper).resetPerpetualTask(any());

    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(Collections.emptyList());
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(helmChartService, times(2)).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper).resetPerpetualTask(any());

    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest));
    settingAttribute = prepareSettingAttribute(HttpHelmRepoConfig.builder().username("1").build());
    currSettingAttribute = prepareSettingAttribute(HttpHelmRepoConfig.builder().username("2").build());
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, times(3)).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, times(2)).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper, times(2)).resetPerpetualTask(any());
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testOnUpdatedNoChanges() {
    SettingAttribute settingAttribute = prepareSettingAttribute("https://charts.helm.sh/stable/");
    SettingAttribute currSettingAttribute = prepareSettingAttribute("https://charts.helm.sh/stable/");
    ApplicationManifest applicationManifest = prepareAppManifest();
    applicationManifest.setPerpetualTaskId(PERPETUAL_TASK_ID);

    enableFeatureFlag();
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest, prepareAppManifest()));
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, never()).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, never()).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper, never()).resetPerpetualTask(any());

    settingAttribute = prepareSettingAttribute(DockerConfig.builder().dockerRegistryUrl("1").build());
    currSettingAttribute = prepareSettingAttribute(DockerConfig.builder().dockerRegistryUrl("2").build());
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(Collections.emptyList());
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, never()).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, never()).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper, never()).resetPerpetualTask(any());
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testOnUpdatedGcs() {
    SettingAttribute settingAttribute = prepareSettingAttribute(GCSHelmRepoConfig.builder().build());
    SettingAttribute currSettingAttribute = prepareSettingAttribute(GCSHelmRepoConfig.builder().build());
    ApplicationManifest applicationManifest = prepareAppManifest();
    applicationManifest.setPerpetualTaskId(PERPETUAL_TASK_ID);

    enableFeatureFlag();
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest, prepareAppManifest()));
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, never()).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, never()).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper, never()).resetPerpetualTask(any());

    settingAttribute = prepareSettingAttribute(GCSHelmRepoConfig.builder().bucketName("1").build());
    currSettingAttribute = prepareSettingAttribute(GCSHelmRepoConfig.builder().bucketName("2").build());
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest, prepareAppManifest()));
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, times(2)).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper).resetPerpetualTask(any());
  }

  @Test
  @Owner(developers = OwnerRule.PRABU)
  @Category(UnitTests.class)
  public void testOnUpdatedAws() {
    SettingAttribute settingAttribute = prepareSettingAttribute(AmazonS3HelmRepoConfig.builder().build());
    SettingAttribute currSettingAttribute = prepareSettingAttribute(AmazonS3HelmRepoConfig.builder().build());
    ApplicationManifest applicationManifest = prepareAppManifest();
    applicationManifest.setPerpetualTaskId(PERPETUAL_TASK_ID);

    enableFeatureFlag();
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest, prepareAppManifest()));
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, never()).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, never()).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper, never()).resetPerpetualTask(any());

    settingAttribute = prepareSettingAttribute(AmazonS3HelmRepoConfig.builder().bucketName("1").build());
    currSettingAttribute = prepareSettingAttribute(AmazonS3HelmRepoConfig.builder().bucketName("2").build());
    when(applicationManifestService.listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID))
        .thenReturn(asList(applicationManifest, prepareAppManifest()));
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, times(2)).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper).resetPerpetualTask(any());

    settingAttribute = prepareSettingAttribute(AmazonS3HelmRepoConfig.builder().region("1").build());
    currSettingAttribute = prepareSettingAttribute(AmazonS3HelmRepoConfig.builder().region("2").build());
    manager.onUpdated(settingAttribute, currSettingAttribute);
    verify(applicationManifestService, times(2)).listHelmChartSourceBySettingId(ACCOUNT_ID, SETTING_ID);
    verify(helmChartService, times(4)).deleteByAppManifest(APP_ID, APP_MANIFEST_ID);
    verify(appManifestPTaskHelper, times(2)).resetPerpetualTask(any());
  }

  private ApplicationManifest prepareAppManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).build();
    applicationManifest.setUuid(APP_MANIFEST_ID);
    applicationManifest.setAppId(APP_ID);
    return applicationManifest;
  }

  private static SettingAttribute prepareSettingAttribute(SettingValue value) {
    return aSettingAttribute().withUuid(SETTING_ID).withValue(value).withAccountId(ACCOUNT_ID).build();
  }

  private static SettingAttribute prepareSettingAttribute(String chartRepoUrl) {
    return prepareSettingAttribute(HttpHelmRepoConfig.builder().chartRepoUrl(chartRepoUrl).build());
  }

  private void enableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
  }

  private void disableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
  }
}
