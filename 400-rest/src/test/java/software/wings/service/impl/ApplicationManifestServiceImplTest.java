/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRAKHAR;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.FETCH;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.PULL;
import static software.wings.beans.HelmCommandFlagConstants.HelmSubCommand.TEMPLATE;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.appmanifest.AppManifestKind.K8S_MANIFEST;
import static software.wings.beans.appmanifest.StoreType.CUSTOM;
import static software.wings.beans.appmanifest.StoreType.HelmChartRepo;
import static software.wings.beans.appmanifest.StoreType.HelmSourceRepo;
import static software.wings.beans.appmanifest.StoreType.KustomizeSourceRepo;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.beans.appmanifest.StoreType.OC_TEMPLATES;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.beans.appmanifest.StoreType.VALUES_YAML_FROM_HELM_REPO;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_MANIFEST_NAME;
import static software.wings.utils.WingsTestConstants.BUCKET_NAME;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.HelmVersion;
import io.harness.manifest.CustomSourceConfig;
import io.harness.persistence.HPersistence;
import io.harness.queue.QueuePublisher;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.Event.Type;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.HelmChartConfig.HelmChartConfigBuilder;
import software.wings.beans.HelmCommandFlagConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.helpers.ext.helm.HelmHelper;
import software.wings.helpers.ext.kustomize.KustomizeConfig;
import software.wings.prune.PruneEvent;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class ApplicationManifestServiceImplTest extends WingsBaseTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Spy @InjectMocks private GitFileConfigHelperService gitFileConfigHelperService;
  @Spy @InjectMocks ApplicationManifestServiceImpl applicationManifestServiceImpl;
  @Mock private AppService appService;
  @Mock private YamlPushService yamlPushService;
  @Mock private SettingsService settingsService;
  @Inject private HPersistence persistence;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private HelmChartService helmChartService;
  @Spy private HelmHelper helmHelper;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private TriggerService triggerService;
  @Mock private QueuePublisher<PruneEvent> pruneQueue;

  @Before
  public void setup() {
    Reflect.on(applicationManifestServiceImpl).set("wingsPersistence", persistence);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(serviceResourceService.get(any(), anyString(), eq(false)))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(false).build());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidateAppManifestForEnvironment() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().kind(AppManifestKind.HELM_CHART_OVERRIDE).storeType(Local).build();
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(Remote);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(HelmChartRepo);
    applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(HelmSourceRepo);
    applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(VALUES_YAML_FROM_HELM_REPO);
    applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setKind(K8S_MANIFEST);
    applicationManifest.setStoreType(HelmChartRepo);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setKind(K8S_MANIFEST);
    applicationManifest.setStoreType(HelmSourceRepo);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(Remote);
    applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(Local);
    applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);

    verifyAppManifestHelmChartOverrideForEnv();
  }

  private void verifyAppManifestHelmChartOverrideForEnv() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().kind(AppManifestKind.HELM_CHART_OVERRIDE).storeType(HelmChartRepo).build();

    applicationManifest.setServiceId(null);
    applicationManifest.setEnvId("envId");
    applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(Local);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(Remote);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(HelmSourceRepo);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);

    applicationManifest.setStoreType(KustomizeSourceRepo);
    verifyExceptionForValidateAppManifestForEnvironment(applicationManifest);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidateApplicationManifest() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .kind(AppManifestKind.HELM_CHART_OVERRIDE)
            .helmChartConfig(HelmChartConfig.builder().chartName("n").connectorId("c1").build())
            .envId("ENVID")
            .storeType(HelmChartRepo)
            .build();

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest));

    applicationManifest.setServiceId("s1");
    doReturn(HelmVersion.V2).when(serviceResourceService).getHelmVersionWithDefault(anyString(), anyString());
    applicationManifestServiceImpl.validateApplicationManifest(applicationManifest);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateApplicationManifest_CommandFlags() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .kind(AppManifestKind.HELM_CHART_OVERRIDE)
            .helmChartConfig(HelmChartConfig.builder().chartName("n").connectorId("c1").build())
            .envId("ENVID")
            .storeType(HelmChartRepo)
            .helmCommandFlag(null)
            .serviceId("s1")
            .build();

    applicationManifest.setAppId("a1");
    doReturn(HelmVersion.V2).when(serviceResourceService).getHelmVersionWithDefault("a1", "s1");
    applicationManifestServiceImpl.validateApplicationManifest(applicationManifest);
    doReturn(null).when(serviceResourceService).getHelmVersionWithDefault("a1", "s1");

    doReturn(HelmVersion.V2).when(serviceResourceService).getHelmVersionWithDefault("a1", "s1");

    HelmCommandFlagConfig helmCommandFlag =
        HelmCommandFlagConfig.builder().valueMap(ImmutableMap.of(TEMPLATE, "")).build();
    applicationManifest.setHelmCommandFlag(helmCommandFlag);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining("Command flag provided is null");
    helmCommandFlag.setValueMap(ImmutableMap.of(PULL, "--debug"));
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining("Invalid subCommand [PULL]");
    helmCommandFlag.setValueMap(ImmutableMap.of(FETCH, "--debug"));
    applicationManifestServiceImpl.validateApplicationManifest(applicationManifest);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidateHelmChartRepoAppManifestForAllServiceOverride() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                                  .storeType(HelmChartRepo)
                                                  .envId("envId")
                                                  .build();

    applicationManifest.setGitFileConfig(GitFileConfig.builder().build());
    doReturn(HelmVersion.V2).when(serviceResourceService).getHelmVersionWithDefault(anyString(), anyString());
    // No GitConfig
    verifyInvalidRequestExceptionWithMessage(applicationManifest, "gitFileConfig cannot be used with HelmChartRepo");

    HelmChartConfig helmChartConfig = helmChartConfigWithConnector().build();
    applicationManifest.setGitFileConfig(null);
    applicationManifest.setHelmChartConfig(helmChartConfig);

    applicationManifestServiceImpl.validateApplicationManifest(applicationManifest);

    helmChartConfig.setConnectorId(null);
    verifyInvalidRequestExceptionWithMessage(applicationManifest, "Helm repository cannot be empty");

    helmChartConfig = helmChartConfigWithConnector().chartName("stable").build();
    applicationManifest.setHelmChartConfig(helmChartConfig);
    verifyInvalidRequestExceptionWithMessage(applicationManifest, "Helm chart name cannot be given");

    helmChartConfig = helmChartConfigWithConnector().chartUrl("http://helm-repo").build();
    applicationManifest.setHelmChartConfig(helmChartConfig);
    verifyInvalidRequestExceptionWithMessage(applicationManifest, "Helm chart url cannot be given");

    helmChartConfig = helmChartConfigWithConnector().chartVersion("1.1").build();
    applicationManifest.setHelmChartConfig(helmChartConfig);
    verifyInvalidRequestExceptionWithMessage(applicationManifest, "Helm chart version cannot be given");
  }

  private HelmChartConfigBuilder helmChartConfigWithConnector() {
    return HelmChartConfig.builder().connectorId("foo");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidateHelmChartRepoAppManifest() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                                  .storeType(HelmChartRepo)
                                                  .serviceId("serviceId")
                                                  .envId("envId")
                                                  .build();
    applicationManifest.setGitFileConfig(GitFileConfig.builder().build());
    // No GitConfig
    verifyExceptionForValidateHelmChartRepoAppManifest(applicationManifest);
    applicationManifest.setGitFileConfig(null);

    // CustomManifest Config
    applicationManifest.setCustomSourceConfig(CustomSourceConfig.builder().build());
    verifyExceptionForValidateHelmChartRepoAppManifest(applicationManifest);
    applicationManifest.setCustomSourceConfig(null);

    HelmChartConfig helmChartConfig = HelmChartConfig.builder().build();
    applicationManifest.setHelmChartConfig(helmChartConfig);
    // Empty connectorId and chartName
    verifyExceptionForValidateHelmChartRepoAppManifest(applicationManifest);

    // Empty chartName
    helmChartConfig.setConnectorId("1");
    verifyExceptionForValidateHelmChartRepoAppManifest(applicationManifest);

    // Empty connectorId
    helmChartConfig.setConnectorId(null);
    helmChartConfig.setChartName("Name");
    verifyExceptionForValidateHelmChartRepoAppManifest(applicationManifest);

    // non-empty url (this is invalid, url needs to be empty)
    helmChartConfig.setConnectorId("con1");
    helmChartConfig.setChartName("Name");
    helmChartConfig.setChartUrl("url");
    verifyExceptionForValidateHelmChartRepoAppManifest(applicationManifest);

    helmChartConfig.setChartUrl(null);
    applicationManifestServiceImpl.validateHelmChartRepoAppManifest(applicationManifest);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testValidateKustomizeApplicationManifest() {
    doReturn(aSettingAttribute().withValue(GitConfig.builder().build()).build()).when(settingsService).get(anyString());
    doReturn(HelmVersion.V2).when(serviceResourceService).getHelmVersionWithDefault(anyString(), anyString());
    applicationManifestServiceImpl.validateApplicationManifest(buildKustomizeAppManifest());
    testEmptyConnectorInRemoteAppManifest(buildKustomizeAppManifest());
    testEmptyCommitInRemoteAppManifest(buildKustomizeAppManifest());
    testEmptyBranchInRemoteAppManifest(buildKustomizeAppManifest());
    testNonEmptyFilePathInGitFileConfig(buildKustomizeAppManifest());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreateAppManifest() {
    doNothing().when(applicationManifestServiceImpl).validateApplicationManifest(any(ApplicationManifest.class));
    doNothing().when(applicationManifestServiceImpl).sanitizeApplicationManifestConfigs(any(ApplicationManifest.class));
    doReturn(false).when(applicationManifestServiceImpl).exists(any(ApplicationManifest.class));
    doReturn("accountId").when(appService).getAccountIdByAppId(anyString());
    doReturn(false).when(featureFlagService).isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, "accountId");

    ApplicationManifest manifest = buildKustomizeAppManifest();
    ApplicationManifest savedApplicationManifest = applicationManifestServiceImpl.create(manifest);

    assertThat(savedApplicationManifest).isNotNull();
    assertThat(manifest).isEqualTo(savedApplicationManifest);
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(eq(null), eq(null), eq(savedApplicationManifest), eq(Type.CREATE), eq(false), eq(false));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateAppManifest() {
    doNothing().when(applicationManifestServiceImpl).validateApplicationManifest(any(ApplicationManifest.class));
    doNothing().when(applicationManifestServiceImpl).sanitizeApplicationManifestConfigs(any(ApplicationManifest.class));
    doNothing().when(applicationManifestServiceImpl).resetReadOnlyProperties(any(ApplicationManifest.class));
    doReturn(true).when(applicationManifestServiceImpl).exists(any(ApplicationManifest.class));
    doReturn("accountId").when(appService).getAccountIdByAppId(any());
    doReturn(false).when(featureFlagService).isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, "accountId");

    ApplicationManifest manifest = buildKustomizeAppManifest();
    ApplicationManifest savedApplicationManifest = applicationManifestServiceImpl.update(manifest);

    assertThat(savedApplicationManifest).isNotNull();
    assertThat(manifest).isEqualTo(savedApplicationManifest);
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(
            any(), eq(savedApplicationManifest), eq(savedApplicationManifest), eq(Type.UPDATE), eq(false), eq(false));
    verify(applicationManifestServiceImpl, times(1)).resetReadOnlyProperties(manifest);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSanitizeKustomizeManifest() {
    verifySanitizeIfNullKustomizeDirPath();
    verifySanitizeIfEmptyKustomizeDirPath();
    verifySanitizeIfNonEmptyKustomizeDirPath();
  }

  private void verifySanitizeIfNonEmptyKustomizeDirPath() {
    ApplicationManifest manifest = buildKustomizeAppManifest();
    String kustomizeDirPath = manifest.getKustomizeConfig().getKustomizeDirPath();
    applicationManifestServiceImpl.sanitizeApplicationManifestConfigs(manifest);
    assertThat(manifest.getKustomizeConfig().getKustomizeDirPath()).isNotNull();
    assertThat(manifest.getKustomizeConfig().getKustomizeDirPath()).isEqualTo(kustomizeDirPath);
  }

  private void verifySanitizeIfEmptyKustomizeDirPath() {
    ApplicationManifest manifest = buildKustomizeAppManifest();
    manifest.getKustomizeConfig().setKustomizeDirPath("");
    applicationManifestServiceImpl.sanitizeApplicationManifestConfigs(manifest);
    assertThat(manifest.getKustomizeConfig().getKustomizeDirPath()).isNotNull();
    assertThat(manifest.getKustomizeConfig().getKustomizeDirPath()).isEmpty();
  }

  private void verifySanitizeIfNullKustomizeDirPath() {
    ApplicationManifest manifest = buildKustomizeAppManifest();
    manifest.getKustomizeConfig().setKustomizeDirPath(null);
    applicationManifestServiceImpl.sanitizeApplicationManifestConfigs(manifest);
    assertThat(manifest.getKustomizeConfig().getKustomizeDirPath()).isNotNull();
    assertThat(manifest.getKustomizeConfig().getKustomizeDirPath()).isEmpty();
  }

  private ApplicationManifest buildKustomizeAppManifest() {
    GitFileConfig gitFileConfig =
        GitFileConfig.builder().connectorId("connector-id").useBranch(true).branch("master").build();
    return ApplicationManifest.builder()
        .kind(K8S_MANIFEST)
        .gitFileConfig(gitFileConfig)
        .storeType(KustomizeSourceRepo)
        .kustomizeConfig(KustomizeConfig.builder().kustomizeDirPath("/root").build())
        .serviceId("serviceId")
        .build();
  }

  private void testEmptyConnectorInRemoteAppManifest(ApplicationManifest applicationManifest) {
    applicationManifest.getGitFileConfig().setConnectorId(null);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining("Connector");
  }

  private void testEmptyCommitInRemoteAppManifest(ApplicationManifest applicationManifest) {
    applicationManifest.getGitFileConfig().setCommitId(null);
    applicationManifest.getGitFileConfig().setUseBranch(false);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining("CommitId");
  }

  private void testEmptyBranchInRemoteAppManifest(ApplicationManifest applicationManifest) {
    applicationManifest.getGitFileConfig().setBranch(null);
    applicationManifest.getGitFileConfig().setUseBranch(true);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining("Branch");
  }

  private void testNonEmptyFilePathInGitFileConfig(ApplicationManifest applicationManifest) {
    applicationManifestServiceImpl.validateApplicationManifest(applicationManifest);
    applicationManifest.getGitFileConfig().setFilePath("foo");
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining("File Path");
  }

  private void verifyExceptionForValidateHelmChartRepoAppManifest(ApplicationManifest applicationManifest) {
    try {
      applicationManifestServiceImpl.validateHelmChartRepoAppManifest(applicationManifest);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }
  }

  private void verifyInvalidRequestExceptionWithMessage(ApplicationManifest applicationManifest, String msg) {
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest))
        .withMessageContaining(msg);
  }

  private void verifyExceptionForValidateAppManifestForEnvironment(ApplicationManifest applicationManifest) {
    try {
      applicationManifestServiceImpl.validateAppManifestForEnvironment(applicationManifest);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testValidateOpenShiftSourceRepoAppManifest() {
    // success validation
    GitFileConfig gitFileConfig =
        GitFileConfig.builder().branch("master").useBranch(true).connectorId("id").filePath("filepath").build();
    GitConfig gitConfig = GitConfig.builder().build();
    doReturn(aSettingAttribute().withValue(gitConfig).build()).when(settingsService).get(anyString());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(OC_TEMPLATES).gitFileConfig(gitFileConfig).build();

    applicationManifestServiceImpl.validateOpenShiftSourceRepoAppManifest(applicationManifest);

    // missing params
    applicationManifest.setGitFileConfig(null);
    assertThatThrownBy(() -> applicationManifestServiceImpl.validateOpenShiftSourceRepoAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Git File Config is mandatory for OpenShift Source Repository Type");
    applicationManifest.setGitFileConfig(gitFileConfig);

    gitFileConfig.setBranch("");
    assertThatThrownBy(() -> applicationManifestServiceImpl.validateOpenShiftSourceRepoAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Branch cannot be empty if useBranch is selected.");
    gitFileConfig.setBranch("master");

    gitFileConfig.setFilePath("");
    assertThatThrownBy(() -> applicationManifestServiceImpl.validateOpenShiftSourceRepoAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Template File Path can't be empty");
    gitFileConfig.setFilePath("filepath");

    gitConfig.setUrlType(GitConfig.UrlType.ACCOUNT);
    assertThatThrownBy(() -> applicationManifestServiceImpl.validateOpenShiftSourceRepoAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Repository name not provided for Account level git connector.");

    gitConfig.setUrlType(GitConfig.UrlType.REPO);
    applicationManifestServiceImpl.validateOpenShiftSourceRepoAppManifest(applicationManifest);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testValidateApplicationManifestGitAccount() {
    GitFileConfig gitFileConfig =
        GitFileConfig.builder().connectorId("connector-id").useBranch(true).branch("master").build();
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .serviceId("s1")
                                                  .kind(AppManifestKind.HELM_CHART_OVERRIDE)
                                                  .envId("ENVID")
                                                  .storeType(HelmSourceRepo)
                                                  .gitFileConfig(gitFileConfig)
                                                  .build();

    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(GitConfig.builder().urlType(GitConfig.UrlType.ACCOUNT).build());

    doReturn(attribute).when(settingsService).get("connector-id");

    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateApplicationManifest(applicationManifest));

    gitFileConfig.setRepoName("repo-name");
    doReturn(HelmVersion.V2).when(serviceResourceService).getHelmVersionWithDefault(anyString(), anyString());
    Service service = Service.builder().deploymentType(DeploymentType.HELM).build();
    doReturn(service).when(serviceResourceService).getWithDetails(any(), any());
    applicationManifestServiceImpl.validateApplicationManifest(applicationManifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testValidateCustomAppManifest() {
    CustomSourceConfig manifestConfig = CustomSourceConfig.builder().build();
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().serviceId("service").envId("env").kind(K8S_MANIFEST).storeType(CUSTOM).build();
    applicationManifest.setAppId("application");
    doReturn("account").when(appService).getAccountIdByAppId("application");
    doReturn(false).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, "account");

    assertThatThrownBy(() -> applicationManifestServiceImpl.validateCustomAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Custom Manifest feature is not enabled");
    doReturn(true).when(featureFlagService).isEnabled(FeatureName.CUSTOM_MANIFEST, "account");

    assertThatThrownBy(() -> applicationManifestServiceImpl.validateCustomAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Custom Source Config is mandatory");

    applicationManifest.setCustomSourceConfig(manifestConfig);
    assertThatThrownBy(() -> applicationManifestServiceImpl.validateCustomAppManifest(applicationManifest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Path can't be empty");

    manifestConfig.setPath("/local");
    assertThatCode(() -> applicationManifestServiceImpl.validateCustomAppManifest(applicationManifest))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testIsHelmRepoOrChartNameChanged() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .kind(AppManifestKind.HELM_CHART_OVERRIDE)
            .helmChartConfig(HelmChartConfig.builder().chartName("n").connectorId("c").build())
            .storeType(HelmChartRepo)
            .envId("envId")
            .build();

    ApplicationManifest applicationManifest1 =
        ApplicationManifest.builder()
            .kind(AppManifestKind.HELM_CHART_OVERRIDE)
            .helmChartConfig(HelmChartConfig.builder().chartName("n1").connectorId("c").build())
            .storeType(HelmChartRepo)
            .envId("envId")
            .build();

    assertThat(applicationManifestServiceImpl.isHelmRepoOrChartNameChanged(applicationManifest, applicationManifest))
        .isFalse();
    assertThat(applicationManifestServiceImpl.isHelmRepoOrChartNameChanged(applicationManifest1, applicationManifest))
        .isTrue();

    HelmChartConfig helmChartConfig = HelmChartConfig.builder().chartVersion("n").connectorId("c1").build();
    applicationManifest1.setHelmChartConfig(helmChartConfig);
    assertThat(applicationManifestServiceImpl.isHelmRepoOrChartNameChanged(applicationManifest1, applicationManifest))
        .isTrue();

    helmChartConfig.setChartName("n1");
    applicationManifest1.setHelmChartConfig(helmChartConfig);
    assertThat(applicationManifestServiceImpl.isHelmRepoOrChartNameChanged(applicationManifest1, applicationManifest))
        .isTrue();
  }

  private void enableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
  }

  private void disableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHandlePollForChangesToggle() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .kind(AppManifestKind.HELM_CHART_OVERRIDE)
            .storeType(HelmSourceRepo)
            .envId("envId")
            .helmChartConfig(HelmChartConfig.builder().chartName("n").connectorId("c").build())
            .pollForChanges(true)
            .build();

    enableFeatureFlag();
    assertThatThrownBy(
        () -> applicationManifestServiceImpl.handlePollForChangesToggle(null, applicationManifest, true, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);

    applicationManifest.setStoreType(HelmChartRepo);

    disableFeatureFlag();
    applicationManifestServiceImpl.handlePollForChangesToggle(null, applicationManifest, true, ACCOUNT_ID);
    verify(applicationManifestServiceImpl, never()).createPerpetualTask(applicationManifest);

    enableFeatureFlag();
    applicationManifestServiceImpl.handlePollForChangesToggle(null, applicationManifest, true, ACCOUNT_ID);
    verify(applicationManifestServiceImpl, times(1)).createPerpetualTask(applicationManifest);

    when(applicationManifestServiceImpl.getById(anyString(), anyString())).thenReturn(applicationManifest);
    applicationManifestServiceImpl.handlePollForChangesToggle(
        applicationManifest, applicationManifest, false, ACCOUNT_ID);
    verify(applicationManifestServiceImpl, times(1)).checkForUpdates(applicationManifest, applicationManifest);
  }

  private ApplicationManifest getHelmChartApplicationManifest() {
    return ApplicationManifest.builder()
        .kind(K8S_MANIFEST)
        .serviceId(SERVICE_ID)
        .storeType(HelmChartRepo)
        .helmChartConfig(HelmChartConfig.builder().chartName("n").connectorId("c").build())
        .build();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCheckForUpdatesWithSavedManifestTrueAndNewManifestNull() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    ApplicationManifest savedAppManifest = getHelmChartApplicationManifest();

    savedAppManifest.setPollForChanges(true);

    when(applicationManifestServiceImpl.getById(any(), any())).thenReturn(savedAppManifest);

    // savedAppManifest -> True, applicationManifest -> null
    applicationManifestServiceImpl.checkForUpdates(savedAppManifest, applicationManifest);
    verify(applicationManifestServiceImpl, times(1)).deletePerpetualTask(savedAppManifest);
    verify(helmChartService, times(1)).deleteByAppManifest(any(), any());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCheckForUpdatesWithSavedManifestTrueAndNewManifestTrueWithDifferentHelmConfig() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    ApplicationManifest savedAppManifest = getHelmChartApplicationManifest();

    savedAppManifest.setPollForChanges(true);
    applicationManifest.setPollForChanges(true);
    applicationManifest.setHelmChartConfig(HelmChartConfig.builder().connectorId("c1").build());

    // savedAppManifest -> True, applicationManifest -> True with different connector id
    when(applicationManifestServiceImpl.getById(any(), any())).thenReturn(savedAppManifest);

    applicationManifestServiceImpl.checkForUpdates(savedAppManifest, applicationManifest);
    verify(helmChartService, times(1)).deleteByAppManifest(any(), any());
    verify(applicationManifestServiceImpl, times(1)).resetPerpetualTask(applicationManifest);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCheckForUpdatesWithSavedManifestTrueAndNewManifestTrueWithSameHelmConfig() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    ApplicationManifest savedAppManifest = getHelmChartApplicationManifest();

    savedAppManifest.setPollForChanges(true);
    applicationManifest.setPollForChanges(true);

    // savedAppManifest -> True, applicationManifest -> True with same connector id and chart name
    when(applicationManifestServiceImpl.getById(anyString(), anyString())).thenReturn(savedAppManifest);
    applicationManifestServiceImpl.checkForUpdates(savedAppManifest, applicationManifest);
    verify(helmChartService, never()).deleteByAppManifest(anyString(), anyString());
    verify(applicationManifestServiceImpl, never()).resetPerpetualTask(applicationManifest);
    verify(applicationManifestServiceImpl, times(1)).createPerpetualTask(applicationManifest);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCheckForUpdatesWithSavedManifestTrueAndNewManifestFalse() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    ApplicationManifest savedAppManifest = getHelmChartApplicationManifest();

    when(applicationManifestServiceImpl.getById(anyString(), anyString())).thenReturn(savedAppManifest);
    savedAppManifest.setPollForChanges(true);
    applicationManifest.setPollForChanges(false);

    // savedAppManifest -> True, applicationManifest -> False
    applicationManifestServiceImpl.checkForUpdates(savedAppManifest, applicationManifest);
    verify(applicationManifestServiceImpl, times(1)).deletePerpetualTask(savedAppManifest);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCheckForUpdatesWithSavedManifestFalseAndNewManifestTrue() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    ApplicationManifest savedAppManifest = getHelmChartApplicationManifest();

    when(applicationManifestServiceImpl.getById(anyString(), anyString())).thenReturn(savedAppManifest);
    savedAppManifest.setPollForChanges(false);
    applicationManifest.setPollForChanges(true);

    // savedAppManifest -> False, applicationManifest -> True
    applicationManifestServiceImpl.checkForUpdates(savedAppManifest, applicationManifest);
    verify(applicationManifestServiceImpl, times(1)).createPerpetualTask(applicationManifest);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testCheckForUpdatesWithSavedManifestNullAndNewManifestTrue() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    ApplicationManifest savedAppManifest = getHelmChartApplicationManifest();

    when(applicationManifestServiceImpl.getById(anyString(), anyString())).thenReturn(savedAppManifest);
    savedAppManifest.setPollForChanges(null);
    applicationManifest.setPollForChanges(true);

    // savedAppManifest -> null, applicationManifest -> True
    applicationManifestServiceImpl.checkForUpdates(savedAppManifest, applicationManifest);
    verify(applicationManifestServiceImpl, times(1)).createPerpetualTask(applicationManifest);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldDeleteHelmChartsOnAppManifestDelete() {
    enableFeatureFlag();
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    applicationManifest.setAccountId(ACCOUNT_ID);
    applicationManifest.setAppId(APP_ID);
    applicationManifest.setPollForChanges(true);
    persistence.save(applicationManifest);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    applicationManifestServiceImpl.deleteAppManifest(APP_ID, applicationManifest.getUuid());
    verify(applicationManifestServiceImpl, times(1)).deletePerpetualTask(applicationManifest);
    verify(applicationManifestServiceImpl, times(1)).deleteAppManifest(applicationManifest);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotCreateOrUpdateWithPollForChangesEnabledAndChartVersionGiven() {
    enableFeatureFlag();
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();

    applicationManifest.setPollForChanges(true);
    applicationManifest.setHelmChartConfig(HelmChartConfig.builder().chartVersion("v1").build());

    assertThatThrownBy(() -> applicationManifestServiceImpl.checkForUpdates(applicationManifest, applicationManifest));

    assertThatThrownBy(()
                           -> applicationManifestServiceImpl.handlePollForChangesToggle(
                               applicationManifest, applicationManifest, true, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No Helm Chart version is required when Poll for Manifest option is enabled.");

    assertThatThrownBy(()
                           -> applicationManifestServiceImpl.handlePollForChangesToggle(
                               applicationManifest, applicationManifest, false, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No Helm Chart version is required when Poll for Manifest option is enabled.");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFetchHelmChartConfigProperties() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .storeType(HelmChartRepo)
            .helmChartConfig(
                HelmChartConfig.builder().connectorId(SETTING_ID).chartName("chart").basePath("base_path").build())
            .build();
    applicationManifest.setUuid(MANIFEST_ID);
    when(applicationManifestServiceImpl.getById(APP_ID, MANIFEST_ID)).thenReturn(applicationManifest);
    GCSHelmRepoConfig helmRepoConfig = GCSHelmRepoConfig.builder().bucketName(BUCKET_NAME).build();
    when(settingsService.get(SETTING_ID))
        .thenReturn(aSettingAttribute().withName(SETTING_NAME).withValue(helmRepoConfig).build());

    Map<String, String> properties = applicationManifestServiceImpl.fetchAppManifestProperties(APP_ID, MANIFEST_ID);
    assertThat(properties)
        .containsEntry("url", "gs://" + BUCKET_NAME + "/base_path")
        .containsEntry("basePath", "base_path")
        .containsEntry("repositoryName", SETTING_NAME)
        .containsEntry("bucketName", BUCKET_NAME);
  }

  private void setUpForListPollingEnabled() {
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();
    applicationManifest.setAppId(APP_ID);
    persistence.save(applicationManifest);

    ApplicationManifest applicationManifest1 = getHelmChartApplicationManifest();
    applicationManifest1.setAppId(APP_ID);
    applicationManifest1.setPollForChanges(true);
    applicationManifest1.setServiceId(SERVICE_ID + 1);
    applicationManifest1.setName(APP_MANIFEST_NAME + 1);
    persistence.save(applicationManifest1);

    ApplicationManifest applicationManifest2 = getHelmChartApplicationManifest();
    applicationManifest2.setAppId(APP_ID);
    applicationManifest2.setPollForChanges(true);
    applicationManifest2.setServiceId(SERVICE_ID + 2);
    applicationManifest2.setName(APP_MANIFEST_NAME + 2);
    persistence.save(applicationManifest2);

    when(serviceResourceService.getServiceNames(anyString(), anySet()))
        .thenReturn(Collections.singletonMap(SERVICE_ID, SERVICE_NAME));
    when(serviceResourceService.getIdsWithArtifactFromManifest(APP_ID))
        .thenReturn(Arrays.asList(SERVICE_ID + 2, SERVICE_ID + 1));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testListPollingEnabled() {
    setUpForListPollingEnabled();
    PageRequest<ApplicationManifest> pageRequest =
        aPageRequest().addFilter("appId", EQ, APP_ID).addFilter("pollForChanges", EQ, true).build();

    PageResponse<ApplicationManifest> pageResponse =
        applicationManifestServiceImpl.listPollingEnabled(pageRequest, APP_ID);
    List<ApplicationManifest> applicationManifestList = pageResponse.getResponse();
    assertThat(applicationManifestList).isNotNull().hasSize(2);
    ApplicationManifest savedAppManifest = applicationManifestList.get(0);
    assertThat(savedAppManifest.getPollForChanges()).isTrue();
    assertThat(applicationManifestList.stream().map(ApplicationManifest::getName))
        .containsExactlyInAnyOrder(APP_MANIFEST_NAME + 1, APP_MANIFEST_NAME + 2);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    applicationManifestServiceImpl.pruneDescendingEntities(APP_ID, MANIFEST_ID);
    InOrder inOrder = inOrder(helmChartService, triggerService);
    inOrder.verify(helmChartService).pruneByApplicationManifest(APP_ID, MANIFEST_ID);
    inOrder.verify(triggerService).pruneByApplicationManifest(APP_ID, MANIFEST_ID);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testSaveApplicationManifest() {
    ApplicationManifest applicationManifest = buildApplicationManifest();

    ManifestFile manifestFile = buildManifestFile(applicationManifest);

    applicationManifestServiceImpl.upsertApplicationManifestFile(manifestFile, applicationManifest, true);
    verify(yamlPushService, times(1)).pushYamlChangeSet(ACCOUNT_ID, null, manifestFile, Type.CREATE, false, false);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRenameApplicationManifest() {
    ApplicationManifest applicationManifest = buildApplicationManifest();

    ManifestFile oldManifestFile = buildManifestFile(applicationManifest);

    persistence.save(oldManifestFile);

    ManifestFile newManifestFile = buildManifestFile(applicationManifest);
    newManifestFile.setUuid(oldManifestFile.getUuid());
    newManifestFile.setFileName(oldManifestFile.getFileName() + "_newName");

    applicationManifestServiceImpl.upsertApplicationManifestFile(newManifestFile, applicationManifest, false);
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(
            eq(ACCOUNT_ID), any(ManifestFile.class), eq(newManifestFile), eq(Type.UPDATE), eq(false), eq(true));
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdateApplicationManifest() {
    ApplicationManifest applicationManifest = buildApplicationManifest();

    ManifestFile oldManifestFile = buildManifestFile(applicationManifest);

    persistence.save(oldManifestFile);

    ManifestFile newManifestFile = buildManifestFile(applicationManifest);
    newManifestFile.setUuid(oldManifestFile.getUuid());
    newManifestFile.setFileContent("name: abc");

    applicationManifestServiceImpl.upsertApplicationManifestFile(newManifestFile, applicationManifest, false);
    verify(yamlPushService, times(1))
        .pushYamlChangeSet(ACCOUNT_ID, oldManifestFile, newManifestFile, Type.UPDATE, false, false);
  }

  @Test
  @Owner(developers = PRAKHAR)
  @Category(UnitTests.class)
  public void testValidateRemoteAppManifest() {
    GitFileConfig gitFileConfig = GitFileConfig.builder().build();
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .storeType(Remote)
                                                  .helmChartConfig(HelmChartConfig.builder().build())
                                                  .gitFileConfig(gitFileConfig)
                                                  .build();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateRemoteAppManifest(applicationManifest))
        .withMessageContaining("helmChartConfig cannot be used with Remote. Use gitFileConfig instead.");

    applicationManifest.setHelmChartConfig(null);
    applicationManifest.setCustomSourceConfig(CustomSourceConfig.builder().build());
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> applicationManifestServiceImpl.validateRemoteAppManifest(applicationManifest))
        .withMessageContaining("customSourceConfig cannot be used with Remote. Use gitFileConfig instead.");

    applicationManifest.setCustomSourceConfig(null);
    doNothing().when(gitFileConfigHelperService).validate(any());
    applicationManifestServiceImpl.validateRemoteAppManifest(applicationManifest);

    applicationManifest.setServiceId("service__1");
    doNothing().when(gitFileConfigHelperService).validate(any());
    when(serviceResourceService.getWithDetails(any(), any())).thenReturn(null);
    applicationManifestServiceImpl.validateRemoteAppManifest(applicationManifest);

    applicationManifest.setAppId("appId");
    Service service = Service.builder().deploymentType(DeploymentType.ECS).build();
    doNothing().when(gitFileConfigHelperService).validate(any());
    when(serviceResourceService.getWithDetails(any(), any())).thenReturn(service);
    doNothing().when(gitFileConfigHelperService).validateEcsGitfileConfig(any());
    applicationManifestServiceImpl.validateRemoteAppManifest(applicationManifest);
    verify(gitFileConfigHelperService, times(1)).validateEcsGitfileConfig(gitFileConfig);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSetPollForChangesFromEnableCollection() {
    enableFeatureFlag();
    when(appService.getAccountIdByAppId(any())).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();

    applicationManifest.setEnableCollection(null);

    when(serviceResourceService.get(any(), any(), eq(false)))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(true).build());

    // savedAppManifest -> False, applicationManifest -> True
    ApplicationManifest appManifest = applicationManifestServiceImpl.create(applicationManifest);
    assertThat(appManifest.getPollForChanges()).isTrue();
    assertThat(appManifest.getEnableCollection()).isTrue();

    applicationManifest.setEnableCollection(true);

    ApplicationManifest appManifest2 = applicationManifestServiceImpl.update(applicationManifest);
    assertThat(appManifest2.getPollForChanges()).isTrue();
    verify(applicationManifestServiceImpl, times(2)).createPerpetualTask(applicationManifest);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSetPollForChangesFromEnableCollectionFalse() {
    enableFeatureFlag();
    when(appService.getAccountIdByAppId(anyString())).thenReturn(ACCOUNT_ID);
    ApplicationManifest applicationManifest = getHelmChartApplicationManifest();

    applicationManifest.setEnableCollection(false);
    when(serviceResourceService.get(any(), anyString(), eq(false)))
        .thenReturn(Service.builder().isK8sV2(true).artifactFromManifest(true).build());

    // savedAppManifest -> False, applicationManifest -> True
    ApplicationManifest appManifest = applicationManifestServiceImpl.create(applicationManifest);
    assertThat(appManifest.getPollForChanges()).isFalse();
    verify(applicationManifestServiceImpl, never()).createPerpetualTask(applicationManifest);
  }

  @NotNull
  private ApplicationManifest buildApplicationManifest() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().storeType(Local).accountId(ACCOUNT_ID).build();
    applicationManifest.setAppId(APP_ID);
    applicationManifest.setUuid(generateUuid());
    return applicationManifest;
  }

  @NotNull
  private ManifestFile buildManifestFile(ApplicationManifest applicationManifest) {
    ManifestFile manifestFile = ManifestFile.builder()
                                    .fileName("abc.yaml")
                                    .accountId(ACCOUNT_ID)
                                    .applicationManifestId(applicationManifest.getUuid())
                                    .build();
    manifestFile.setAppId(APP_ID);
    return manifestFile;
  }
}
