/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.artifact.Artifact;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Tests the WorkflowStandardParamsExtensionService.
 */
public class WorkflowStandardParamsExtensionServiceTest extends WingsBaseTest {
  @Inject @InjectMocks AppService appService;
  @Inject EnvironmentService environmentService;

  @Inject Injector injector;

  @Mock SettingsService settingsService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private FeatureFlagService featureFlagService;

  @Before
  public void setup() {
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.APP_DYNAMICS.name()))
        .thenReturn(Lists.newArrayList(aSettingAttribute().withUuid("id").build()));
    on(appService).set("settingsService", settingsService);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredApp() {
    when(limitCheckerFactory.getInstance(any())).thenReturn(mockChecker());
    Application app = prepareApp();
    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(app.getUuid());
    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, null, null);
    Application fetchedApp = extensionService.fetchRequiredApp(std);
    assertThat(fetchedApp).isNotNull();
    assertThat(fetchedApp.getName()).isEqualTo(app.getName());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldThrowOnFetchRequiredApp() {
    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(null);
    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, null, null);
    extensionService.fetchRequiredApp(std);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchRequiredEnv() {
    when(limitCheckerFactory.getInstance(any())).thenReturn(mockChecker());
    Application app = prepareApp();
    Environment env = prepareEnv(app.getUuid());
    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(app.getUuid());
    std.setEnvId(env.getUuid());
    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, null, null);
    Environment fetchedEnv = extensionService.fetchRequiredEnv(std);
    assertThat(fetchedEnv).isNotNull();
    assertThat(fetchedEnv.getName()).isEqualTo(env.getName());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldThrowOnFetchRequiredEnv() {
    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setEnvId(null);
    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, null, null);
    extensionService.fetchRequiredEnv(std);
  }

  private Application prepareApp() {
    return appService.save(anApplication().name("app_name").accountId(ACCOUNT_ID).build());
  }

  private Environment prepareEnv(String appId) {
    return environmentService.save(anEnvironment().appId(appId).name("env_name").build());
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetArtifactForService() {
    when(artifactService.get(ARTIFACT_ID))
        .thenReturn(
            anArtifact().withUuid(ARTIFACT_ID).withAppId(APP_ID).withArtifactStreamId(ARTIFACT_STREAM_ID).build());
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList(ARTIFACT_STREAM_ID));

    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(APP_ID);
    std.setArtifactIds(singletonList(ARTIFACT_ID));

    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, artifactService, artifactStreamServiceBindingService);
    Artifact artifact = extensionService.getArtifactForService(std, SERVICE_ID);
    assertThat(artifact).isNotNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldGetRollbackArtifactForService() {
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID))
        .thenReturn(singletonList("ROLLBACK_ARTIFACT_STREAM_ID"));
    when(artifactService.get("ROLLBACK_ARTIFACT_ID"))
        .thenReturn(anArtifact()
                        .withUuid("ROLLBACK_ARTIFACT_ID")
                        .withAppId(APP_ID)
                        .withArtifactStreamId("ROLLBACK_ARTIFACT_STREAM_ID")
                        .build());

    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(APP_ID);
    std.setRollbackArtifactIds(singletonList("ROLLBACK_ARTIFACT_ID"));

    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, artifactService, artifactStreamServiceBindingService);
    Artifact rollbackArtifact = extensionService.getRollbackArtifactForService(std, SERVICE_ID);
    assertThat(rollbackArtifact).isNotNull();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldGetNullArtifact() {
    when(artifactService.get(ARTIFACT_ID)).thenReturn(anArtifact().withUuid(ARTIFACT_ID).withAppId(APP_ID).build());
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID)).thenReturn(new ArrayList<>());
    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(APP_ID);
    std.setArtifactIds(singletonList(ARTIFACT_ID));

    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, artifactService, artifactStreamServiceBindingService);
    Artifact artifact = extensionService.getArtifactForService(std, SERVICE_ID);
    assertThat(artifact).isNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void shouldGetNullRollbackArtifact() {
    when(artifactService.get("ROLLBACK_ARTIFACT_ID"))
        .thenReturn(anArtifact().withUuid("ROLLBACK_ARTIFACT_ID").withAppId(APP_ID).build());
    when(artifactStreamServiceBindingService.listArtifactStreamIds(SERVICE_ID)).thenReturn(new ArrayList<>());
    WorkflowStandardParams std = new WorkflowStandardParams();
    std.setAppId(APP_ID);
    std.setRollbackArtifactIds(singletonList("ROLLBACK_ARTIFACT_ID"));

    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, artifactService, artifactStreamServiceBindingService);
    Artifact rollbackArtifact = extensionService.getRollbackArtifactForService(std, SERVICE_ID);
    assertThat(rollbackArtifact).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void test_getArtifacts() {
    List<String> artifactIds = new ArrayList<>();
    artifactIds.add("id1");
    artifactIds.add("id2");
    WorkflowStandardParams workflowStandardParams =
        WorkflowStandardParams.Builder.aWorkflowStandardParams().withArtifactIds(artifactIds).withAppId(APP_ID).build();
    Artifact artifact1 =
        Artifact.Builder.anArtifact().withUuid("id1").withAccountId(ACCOUNT_ID).withLastUpdatedAt(10).build();
    Artifact artifact2 =
        Artifact.Builder.anArtifact().withUuid("id2").withAccountId(ACCOUNT_ID).withLastUpdatedAt(20).build();
    when(artifactService.get("id1")).thenReturn(artifact1);
    when(artifactService.get("id2")).thenReturn(artifact2);
    when(featureFlagService.isEnabled(FeatureName.SORT_ARTIFACTS_IN_UPDATED_ORDER, null)).thenReturn(true);
    WorkflowStandardParamsExtensionService extensionService =
        this.getWorkflowStandardParamsExtensionService(null, artifactService, artifactStreamServiceBindingService);
    List<Artifact> artifacts = extensionService.getArtifacts(workflowStandardParams);
    assertThat(artifacts.get(0)).isEqualTo(artifact2);
    assertThat(artifacts.get(1)).isEqualTo(artifact1);
  }

  private WorkflowStandardParamsExtensionService getWorkflowStandardParamsExtensionService(
      @Nullable AccountService accountService, @Nullable ArtifactService artifactService,
      @Nullable ArtifactStreamServiceBindingService artifactStreamServiceBindingService) {
    return new WorkflowStandardParamsExtensionService(this.injector.getInstance(AppService.class),
        accountService != null ? accountService : this.injector.getInstance(AccountService.class),
        artifactService != null ? artifactService : this.injector.getInstance(ArtifactService.class),
        injector.getInstance(EnvironmentService.class),
        artifactStreamServiceBindingService != null ? artifactStreamServiceBindingService
                                                    : injector.getInstance(ArtifactStreamServiceBindingService.class),
        injector.getInstance(HelmChartService.class), featureFlagService);
  }
}
