/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.exception.GeneralException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.api.InfraMappingElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class HelmChartConfigHelperServiceTest extends WingsBaseTest {
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SettingsService mockSettingsService;
  @Mock private SecretManager mockSecretManager;
  @Mock private ExecutionContext executionContext;
  @Mock private FeatureFlagService mockFeatureFlagService;

  private SettingAttribute helmConnector;

  @InjectMocks @Inject private HelmChartConfigHelperService helmChartConfigHelperService;

  @Before
  public void setUp() {
    when(executionContext.renderExpression(anyString()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(executionContext.getWorkflowExecutionId()).thenReturn(WingsTestConstants.WORKFLOW_EXECUTION_ID);
    when(executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(WingsTestConstants.SERVICE_ID).build())
                        .build());
    when(serviceResourceService.getHelmVersionWithDefault(APP_ID, WingsTestConstants.SERVICE_ID))
        .thenReturn(HelmVersion.V3);

    helmConnector = new SettingAttribute();
    helmConnector.setName("helm-connector");
    helmConnector.setUuid("abc");
    helmConnector.setValue(HttpHelmRepoConfig.builder().build());

    when(mockSettingsService.get(anyString())).thenReturn(helmConnector);
    when(mockSettingsService.getByName(eq(ACCOUNT_ID), eq(APP_ID), anyString())).thenReturn(helmConnector);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldAlwaysSetHelmVersionInHelmChartConfigTaskParams() {
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .helmChartConfig(HelmChartConfig.builder()
                                                                       .chartUrl("abc")
                                                                       .chartName("test")
                                                                       .connectorId("abc")
                                                                       .chartVersion("0.1")
                                                                       .build())
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .build();
    when(executionContext.fetchInfraMappingId()).thenReturn("svcId_envId");
    when(executionContext.fetchInfraMappingElement()).thenReturn(InfraMappingElement.builder().name("infraId").build());
    HelmChartConfigParams helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getHelmVersion()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldAlwaysSetHelmVersionInHelmChartConfigTaskParamsIfNullConnectorId() {
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .helmChartConfig(HelmChartConfig.builder().chartUrl("abc").chartName("test").chartVersion("0.1").build())
            .storeType(StoreType.HelmChartRepo)
            .build();
    HelmChartConfigParams helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getHelmVersion()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldRenderChartConfigFields() {
    final String placeHolder = "${abc}";
    final String placeHolderValue = "foo";

    when(executionContext.renderExpression(placeHolder)).thenReturn(placeHolderValue);

    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .helmChartConfig(HelmChartConfig.builder()
                                                                       .chartUrl(placeHolder)
                                                                       .chartName(placeHolder)
                                                                       .chartVersion(placeHolder)
                                                                       .build())
                                                  .storeType(StoreType.HelmSourceRepo)
                                                  .build();

    HelmChartConfigParams helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);

    assertThat(helmChartConfigTaskParams.getChartUrl()).isEqualTo(placeHolderValue);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo(placeHolderValue);
    assertThat(helmChartConfigTaskParams.getChartVersion()).isEqualTo(placeHolderValue);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmChartConfigFromYamlIfNullConfig() {
    assertThat(helmChartConfigHelperService.getHelmChartConfigFromYaml(ACCOUNT_ID, APP_ID, null)).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmChartConfigForToYamlIfNullConfig() {
    assertThat(helmChartConfigHelperService.getHelmChartConfigForToYaml(null)).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmChartConfigTaskParamsIfNullConfig() {
    assertThat(helmChartConfigHelperService.getHelmChartConfigTaskParams(
                   executionContext, ApplicationManifest.builder().storeType(StoreType.HelmSourceRepo).build()))
        .isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmChartConfigFromYaml() {
    HelmChartConfig helmChartConfig = HelmChartConfig.builder()
                                          .chartName("apache")
                                          .chartUrl("foo.com")
                                          .basePath("abc")
                                          .connectorName("git_helm")
                                          .chartVersion("42.42.42")
                                          .build();
    HelmChartConfig helmChartConfigFromYaml =
        helmChartConfigHelperService.getHelmChartConfigFromYaml(ACCOUNT_ID, APP_ID, helmChartConfig);

    assertThat(helmChartConfigFromYaml).isNotNull();
    assertThat(helmChartConfigFromYaml.getConnectorId()).isEqualTo(helmConnector.getUuid());
    assertThat(helmChartConfigFromYaml.getChartName()).isEqualTo(helmChartConfig.getChartName());
    assertThat(helmChartConfigFromYaml.getChartUrl()).isEqualTo(helmChartConfig.getChartUrl());
    assertThat(helmChartConfigFromYaml.getChartVersion()).isEqualTo(helmChartConfig.getChartVersion());
    assertThat(helmChartConfigFromYaml.getConnectorName()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetHelmChartConfigForToYaml() {
    HelmChartConfig helmChartConfig = HelmChartConfig.builder()
                                          .chartName("apache")
                                          .chartUrl("foo.com")
                                          .basePath("abc")
                                          .connectorId(helmConnector.getUuid())
                                          .chartVersion("42.42.42")
                                          .build();

    HelmChartConfig helmChartConfigForToYaml =
        helmChartConfigHelperService.getHelmChartConfigForToYaml(helmChartConfig);

    assertThat(helmChartConfigForToYaml).isNotNull();
    assertThat(helmChartConfigForToYaml.getConnectorName()).isNotNull();
    assertThat(helmChartConfigForToYaml.getConnectorId()).isNull();
    assertThat(helmChartConfigForToYaml.getChartName()).isEqualTo(helmChartConfig.getChartName());
    assertThat(helmChartConfigForToYaml.getChartUrl()).isEqualTo(helmChartConfig.getChartUrl());
    assertThat(helmChartConfigForToYaml.getChartVersion()).isEqualTo(helmChartConfig.getChartVersion());
  }

  @Test
  @Owner(developers = OwnerRule.ANSHUL)
  @Category(UnitTests.class)
  public void testGetHelmChartConfigTaskParamsWithDeletedParentCP() {
    ApplicationManifest appManifest =
        ApplicationManifest.builder()
            .helmChartConfig(
                HelmChartConfig.builder().connectorId("connectorId").chartName("test").chartVersion("0.1").build())
            .storeType(StoreType.HelmChartRepo)
            .build();

    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setName("s3-helm-connector");
    settingAttribute.setUuid("abc");
    settingAttribute.setValue(AmazonS3HelmRepoConfig.builder().connectorId("cloudProviderId").build());

    when(mockSettingsService.get(anyString())).thenReturn(settingAttribute);
    when(mockSettingsService.get("cloudProviderId")).thenReturn(null);
    when(executionContext.fetchInfraMappingId()).thenReturn("svcId_envId");
    when(executionContext.fetchInfraMappingElement()).thenReturn(InfraMappingElement.builder().name("infraId").build());

    try {
      helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, appManifest);
      fail("Should not reach here.");
    } catch (GeneralException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("Cloud provider deleted for helm repository connector [s3-helm-connector] selected in service");
    }
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void shouldHandleChartNamesProperly() {
    when(executionContext.renderExpression(anyString())).thenAnswer(i -> i.getArgument(0, String.class));
    when(mockFeatureFlagService.isEnabled(ArgumentMatchers.eq(FeatureName.HELM_CHART_NAME_SPLIT), any()))
        .thenReturn(true);
    when(executionContext.fetchInfraMappingId()).thenReturn("svcId_envId");
    when(executionContext.fetchInfraMappingElement()).thenReturn(InfraMappingElement.builder().name("infraId").build());
    handleChartNameForHelmRepo();
    handleChartNameForNoneRepoAndUrl();
    handleChartNameForRepoAlreadyInstalledOnDelegate();
  }

  private void handleChartNameForRepoAlreadyInstalledOnDelegate() {
    HelmChartConfigParams helmChartConfigTaskParams;
    ApplicationManifest applicationManifest = ApplicationManifest.builder()
                                                  .helmChartConfig(HelmChartConfig.builder().chartName("vault").build())
                                                  .storeType(StoreType.HelmChartRepo)
                                                  .build();

    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("vault");

    applicationManifest.setHelmChartConfig(HelmChartConfig.builder().chartName("stable/vault").build());
    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("vault");

    applicationManifest.setHelmChartConfig(
        HelmChartConfig.builder().connectorId("").chartUrl("").chartName("stable/vault").build());
    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("vault");
  }

  private void handleChartNameForNoneRepoAndUrl() {
    HelmChartConfigParams helmChartConfigTaskParams;
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .helmChartConfig(HelmChartConfig.builder().chartUrl("charts.helm.sh").chartName("vault").build())
            .storeType(StoreType.HelmChartRepo)
            .build();

    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("vault");

    applicationManifest.setHelmChartConfig(
        HelmChartConfig.builder().chartUrl("charts.helm.sh").chartName("stable/vault").build());
    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("stable/vault");
  }

  private void handleChartNameForHelmRepo() {
    HelmChartConfigParams helmChartConfigTaskParams;
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder()
            .helmChartConfig(HelmChartConfig.builder().connectorId("connectorId").chartName("vault").build())
            .storeType(StoreType.HelmChartRepo)
            .build();

    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("vault");

    applicationManifest.setHelmChartConfig(
        HelmChartConfig.builder().connectorId("connectorId").chartName("stable/vault").build());
    helmChartConfigTaskParams =
        helmChartConfigHelperService.getHelmChartConfigTaskParams(executionContext, applicationManifest);
    assertThat(helmChartConfigTaskParams.getChartName()).isEqualTo("stable/vault");
  }
}
