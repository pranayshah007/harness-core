/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_REGISTRY_URL_KEY;
import static software.wings.beans.config.ArtifactSourceable.ARTIFACT_SOURCE_USER_NAME_KEY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_KEY;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ServiceElement;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.Artifact;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Tests the WorkflowStandardParamsMapper class.
 *
 * @author Rishi
 */
public class WorkflowStandardParamsMapperTest extends WingsBaseTest {
  @Inject @InjectMocks AppService appService;
  @Inject EnvironmentService environmentService;
  @Inject Injector injector;

  @Mock SettingsService settingsService;
  @Mock private ExecutionContext context;
  @Mock private ArtifactService artifactService;
  @Mock private AccountService accountService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private HelmChartService helmChartService;
  @Mock private ApplicationManifestService applicationManifestService;

  @Before
  public void setup() {
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.APP_DYNAMICS.name()))
        .thenReturn(Lists.newArrayList(aSettingAttribute().withUuid("id").build()));
    on(appService).set("settingsService", settingsService);
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, ACCOUNT_ID)).thenReturn(false);
  }

  /**
   * Should get app.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldGetApp() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).build();
    app = appService.save(app);

    Environment env = anEnvironment().appId(app.getUuid()).name("DEV").build();
    env = environmentService.save(env);
    app = appService.get(app.getUuid());

    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(app.getUuid());
    std.setEnvId(env.getUuid());

    ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    when(context.getContextElement(ContextElementType.SERVICE)).thenReturn(serviceElement);

    Map<String, Object> map =
        this.getWorkflowStandardParamsParamMapper(std, null, null, null, null, null, null, null).paramMap(context);
    assertThat(map).isNotNull().containsEntry(ContextElement.APP, app).containsEntry(ContextElement.ENV, env);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetArtifactWithSourceProperties() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).build();
    app = appService.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).name("DEV").build();
    env = environmentService.save(env);

    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(app.getUuid());
    std.setEnvId(env.getUuid());
    List<String> artifactIds = new ArrayList<>();
    artifactIds.add(ARTIFACT_ID);
    std.setArtifactIds(artifactIds);

    ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    when(context.getContextElement(ContextElementType.SERVICE)).thenReturn(serviceElement);
    when(artifactService.get(ARTIFACT_ID))
        .thenReturn(anArtifact()
                        .withUuid(ARTIFACT_ID)
                        .withAppId(app.getUuid())
                        .withArtifactStreamId(ARTIFACT_STREAM_ID)
                        .build());
    when(artifactStreamService.fetchArtifactSourceProperties(app.getAccountId(), ARTIFACT_STREAM_ID))
        .thenReturn(ImmutableMap.of(
            ARTIFACT_SOURCE_USER_NAME_KEY, "harness", ARTIFACT_SOURCE_REGISTRY_URL_KEY, "http://docker.registry.io"));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));

    Map<String, Object> map = this.getWorkflowStandardParamsParamMapper(std, artifactService, artifactStreamService,
                                      featureFlagService, null, null, null, artifactStreamServiceBindingService)
                                  .paramMap(context);
    assertThat(map).isNotNull().containsKey(ContextElement.ARTIFACT);
    Artifact artifact = (Artifact) map.get(ContextElement.ARTIFACT);
    assertThat(artifact).isNotNull();
    assertThat(artifact.getSource())
        .isNotEmpty()
        .containsKeys(ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldGetRollbackArtifactWithSourceProperties() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).build();
    app = appService.save(app);
    Environment env = anEnvironment().appId(app.getUuid()).name("DEV").build();
    env = environmentService.save(env);

    WorkflowStandardParams std = new WorkflowStandardParams();

    std.setAppId(app.getUuid());
    std.setEnvId(env.getUuid());
    std.setRollbackArtifactIds(singletonList("ROLLBACK_ARTIFACT_ID"));

    ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID).name(SERVICE_NAME).build();
    when(context.getContextElement(ContextElementType.SERVICE)).thenReturn(serviceElement);
    when(artifactService.get("ROLLBACK_ARTIFACT_ID"))
        .thenReturn(anArtifact()
                        .withUuid("ROLLBACK_ARTIFACT_ID")
                        .withAppId(app.getUuid())
                        .withArtifactStreamId("ROLLBACK_ARTIFACT_STREAM_ID")
                        .build());
    when(artifactStreamService.fetchArtifactSourceProperties(app.getAccountId(), "ROLLBACK_ARTIFACT_STREAM_ID"))
        .thenReturn(ImmutableMap.of(
            ARTIFACT_SOURCE_USER_NAME_KEY, "harness", ARTIFACT_SOURCE_REGISTRY_URL_KEY, "http://docker.registry.io"));
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList("ROLLBACK_ARTIFACT_STREAM_ID"));

    Map<String, Object> map = this.getWorkflowStandardParamsParamMapper(std, artifactService, artifactStreamService,
                                      featureFlagService, null, null, null, artifactStreamServiceBindingService)
                                  .paramMap(context);
    assertThat(map).isNotNull().containsKey(ContextElement.ROLLBACK_ARTIFACT);
    Artifact rollbackArtifact = (Artifact) map.get(ContextElement.ROLLBACK_ARTIFACT);
    assertThat(rollbackArtifact).isNotNull();
    assertThat(rollbackArtifact.getSource())
        .isNotEmpty()
        .containsKeys(ARTIFACT_SOURCE_USER_NAME_KEY, ARTIFACT_SOURCE_REGISTRY_URL_KEY);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetAccountDefaults() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).build();
    app = appService.save(app);

    Environment env = anEnvironment().appId(app.getUuid()).name("DEV").build();
    env = environmentService.save(env);

    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(app.getUuid());
    std.setEnvId(env.getUuid());

    when(accountService.getAccountWithDefaults(ACCOUNT_ID))
        .thenReturn(Account.Builder.anAccount()
                        .withAccountKey(ACCOUNT_KEY)
                        .withCompanyName(COMPANY_NAME)
                        .withUuid(ACCOUNT_ID)
                        .withDefaults(ImmutableMap.of("MyActDefaultVar", "MyDefaultValue"))
                        .build());

    Map<String, Object> map =
        this.getWorkflowStandardParamsParamMapper(std, null, null, null, null, accountService, null, null)
            .paramMap(context);
    assertThat(map).isNotNull().containsKey(ContextElement.ACCOUNT);
    Account account = (Account) map.get(ContextElement.ACCOUNT);
    assertThat(account).isNotNull();
    assertThat(account.getDefaults()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSetCorrectHelmChartInContext() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    WorkflowStandardParams std = new WorkflowStandardParams();

    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).build();
    app = appService.save(app);
    std.setAppId(app.getUuid());
    List<String> helmChartIds = Arrays.asList(HELM_CHART_ID + 1, HELM_CHART_ID + 2);
    std.setHelmChartIds(helmChartIds);
    String chartName = "chart";
    HelmChart helmChart1 = createHelmChart(chartName, 1);
    HelmChart helmChart2 = createHelmChart(chartName, 2);
    ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID + 1).name(SERVICE_NAME).build();

    when(context.fetchServiceElement()).thenReturn(serviceElement);
    when(helmChartService.listByIds(ACCOUNT_ID, helmChartIds)).thenReturn(Arrays.asList(helmChart1, helmChart2));
    when(applicationManifestService.fetchAppManifestProperties(app.getUuid(), MANIFEST_ID))
        .thenReturn(Collections.singletonMap("url", "helmRepoUrl"));
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);

    Map<String, Object> paramMap = this.getWorkflowStandardParamsParamMapper(std, null, null, featureFlagService,
                                           applicationManifestService, null, helmChartService, null)
                                       .paramMap(context);
    HelmChart helmChart = (HelmChart) paramMap.get(ContextElement.HELM_CHART);
    assertThat(helmChart).isNotNull();
    assertThat(helmChart.getUuid()).isEqualTo(HELM_CHART_ID + 1);
    assertThat(helmChart.getName()).isEqualTo(chartName);
    assertThat(helmChart.getMetadata().get("url")).isEqualTo("helmRepoUrl");
  }

  private HelmChart createHelmChart(String chartName, int version) {
    return HelmChart.builder()
        .serviceId(SERVICE_ID + version)
        .applicationManifestId(MANIFEST_ID)
        .uuid(HELM_CHART_ID + version)
        .name(chartName)
        .build();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSetNullHelmChartIfAbsent() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    WorkflowStandardParams std = new WorkflowStandardParams();

    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).build();
    app = appService.save(app);
    std.setAppId(app.getUuid());

    List<String> helmChartIds = Arrays.asList(HELM_CHART_ID + 1);
    String chartName = "chart";
    HelmChart helmChart1 = createHelmChart(chartName, 1);

    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(false);
    Map<String, Object> paramMap = this.getWorkflowStandardParamsParamMapper(
                                           std, null, null, featureFlagService, null, null, helmChartService, null)
                                       .paramMap(context);
    assertThat(paramMap.get(ContextElement.HELM_CHART)).isNull();

    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    paramMap = this.getWorkflowStandardParamsParamMapper(
                       std, null, null, featureFlagService, null, null, helmChartService, null)
                   .paramMap(context);
    assertThat(paramMap.get(ContextElement.HELM_CHART)).isNull();

    std.setHelmChartIds(Arrays.asList(HELM_CHART_ID + 1));
    ServiceElement serviceElement = ServiceElement.builder().uuid(SERVICE_ID + 2).name(SERVICE_NAME).build();
    when(context.fetchServiceElement()).thenReturn(serviceElement);
    when(helmChartService.listByIds(ACCOUNT_ID, helmChartIds)).thenReturn(Arrays.asList(helmChart1));
    paramMap = this.getWorkflowStandardParamsParamMapper(
                       std, null, null, featureFlagService, null, null, helmChartService, null)
                   .paramMap(context);
    assertThat(paramMap.get(ContextElement.HELM_CHART)).isNull();
    verify(helmChartService).listByIds(ACCOUNT_ID, helmChartIds);
  }

  private WorkflowStandardParamsParamMapper getWorkflowStandardParamsParamMapper(WorkflowStandardParams params,
      @Nullable ArtifactService artifactService, @Nullable ArtifactStreamService artifactStreamService,
      @Nullable FeatureFlagService featureFlagService, @Nullable ApplicationManifestService applicationManifestService,
      @Nullable AccountService accountService, @Nullable HelmChartService helmChartService,
      @Nullable ArtifactStreamServiceBindingService artifactStreamServiceBindingService) {
    WorkflowStandardParamsExtensionService extensionService = new WorkflowStandardParamsExtensionService(
        this.injector.getInstance(AppService.class),
        accountService != null ? accountService : this.injector.getInstance(AccountService.class),
        artifactService != null ? artifactService : this.injector.getInstance(ArtifactService.class),
        injector.getInstance(EnvironmentService.class),
        artifactStreamServiceBindingService != null ? artifactStreamServiceBindingService
                                                    : injector.getInstance(ArtifactStreamServiceBindingService.class),
        helmChartService != null ? helmChartService : injector.getInstance(HelmChartService.class));

    return new WorkflowStandardParamsParamMapper(this.injector.getInstance(SubdomainUrlHelperIntfc.class),
        this.injector.getInstance(WorkflowExecutionService.class),
        artifactService != null ? artifactService : this.injector.getInstance(ArtifactService.class),
        artifactStreamService != null ? artifactStreamService : this.injector.getInstance(ArtifactStreamService.class),
        applicationManifestService != null ? applicationManifestService
                                           : this.injector.getInstance(ApplicationManifestService.class),
        featureFlagService != null ? featureFlagService : this.injector.getInstance(FeatureFlagService.class),
        this.injector.getInstance(BuildSourceService.class), extensionService, params);
  }
}
