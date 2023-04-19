/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.governance;

import static io.harness.rule.OwnerRule.ATHARVA;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.HarnessException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.governance.AllAppFilter;
import io.harness.governance.AllEnvFilter;
import io.harness.governance.AllNonProdEnvFilter;
import io.harness.governance.AllProdEnvFilter;
import io.harness.governance.ApplicationFilter;
import io.harness.governance.BlackoutWindowFilterType;
import io.harness.governance.CustomAppFilter;
import io.harness.governance.CustomEnvFilter;
import io.harness.governance.EnvironmentFilter;
import io.harness.governance.EnvironmentFilter.EnvironmentFilterType;
import io.harness.governance.ServiceFilter;
import io.harness.governance.ServiceFilter.ServiceFilterType;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.security.UserGroup;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.WorkflowYAMLHelper;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.handler.trigger.ArtifactSelectionYamlHandler;
import software.wings.service.impl.yaml.handler.trigger.ManifestSelectionYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;
import software.wings.yaml.handler.YamlHandlerTestBase;
import software.wings.yaml.trigger.ArtifactTriggerConditionHandler;
import software.wings.yaml.trigger.ManifestTriggerConditionHandler;
import software.wings.yaml.trigger.PipelineTriggerConditionHandler;
import software.wings.yaml.trigger.ScheduledTriggerConditionHandler;
import software.wings.yaml.trigger.WebhookTriggerConditionHandler;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GovernanceConfigYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private YamlHelper mockYamlHelper;
  @Mock private YamlHandlerFactory mockYamlHandlerFactory;

  @Mock private AppService appService;
  @Mock private GovernanceConfigService governanceConfigService;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private EnvironmentService environmentService;
  @Mock private InfrastructureDefinitionService infrastructureDefinitionService;

  @Mock private SettingsService settingsService;

  @Mock private UserGroupService userGroupService;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks @Inject private GovernanceConfigYamlHandler handler;
  @InjectMocks @Inject private TimeRangeBasedFreezeConfigYamlHandler timeRangeBasedFreezeConfigYamlHandler;
  @InjectMocks @Inject private CustomEnvFilterYamlHandler customEnvFilterYamlHandler;
  @InjectMocks @Inject private CustomAppFilterYamlHandler customAppFilterYamlHandler;
  @InjectMocks @Inject private AllAppFilterYamlHandler allAppFilterYamlHandler;
  @InjectMocks @Inject private AllEnvFilterYamlHandler allEnvFilterYamlHandler;
  @InjectMocks @Inject private AllProdEnvFilterYamlHandler allProdEnvFilterYamlHandler;
  @InjectMocks @Inject private AllNonProdEnvFilterYamlHandler allNonProdEnvFilterYamlHandler;
  @InjectMocks @Inject private ServiceFilterYamlHandler serviceFilterYamlHandler;

  @InjectMocks @Inject private ArtifactTriggerConditionHandler artifactTriggerConditionHandler;
  @InjectMocks @Inject private PipelineTriggerConditionHandler pipelineTriggerConditionHandler;
  @InjectMocks @Inject private ScheduledTriggerConditionHandler scheduledTriggerConditionHandler;
  @InjectMocks @Inject private ManifestTriggerConditionHandler manifestTriggerConditionHandler;
  @InjectMocks @Inject private WorkflowYAMLHelper workflowYAMLHelper;

  @InjectMocks @Inject private WebhookTriggerConditionHandler webhookTriggerConditionHandler;
  @InjectMocks @Inject private ArtifactSelectionYamlHandler artifactSelectionYamlHandler;
  @InjectMocks @Inject private ManifestSelectionYamlHandler manifestSelectionYamlHandler;

  private final String yamlFilePath = "Setup/Governance/Deployment Governance.yaml";
  private final String resourcePath = "400-rest/src/test/resources/governance";

  @UtilityClass
  private static class validGovernanceConfigFiles {
    // Custom App Custom Env filter. Success
    private final String GovernanceConfig1 = "governance_config1.yaml";
    // Custom App Custom Env filter. Error: More than 1 app for custom env filter
    private final String GovernanceConfig2 = "governance_config2.yaml";
    // Custom App All Env filter.
    private final String GovernanceConfig3 = "governance_config3.yaml";
    // Custom App All Prod Env filter.
    private final String GovernanceConfig4 = "governance_config4.yaml";
    // Custom App All Non Prod Env filter.
    private final String GovernanceConfig5 = "governance_config5.yaml";
    // Custom App All Non Prod Env filter with environments also given
    private final String GovernanceConfig6 = "governance_config6.yaml";
    // All App All Env filter
    private final String GovernanceConfig7 = "governance_config7.yaml";
    // All App All Prod Env filter
    private final String GovernanceConfig8 = "governance_config8.yaml";
    // All App All Non Prod Env filter
    private final String GovernanceConfig9 = "governance_config9.yaml";
    // All App CUSTOM Env Filter Error
    private final String GovernanceConfig10 = "governance_config10.yaml";
    // Unknown App
    private final String GovernanceConfig11 = "governance_config11.yaml";
    // Unknown Env
    private final String GovernanceConfig12 = "governance_config12.yaml";
    // Window name absent
    private final String GovernanceConfig13 = "governance_config13.yaml";
    // Start time less than End Time
    private final String GovernanceConfig15 = "governance_config15.yaml";
    // Freeze duration is more than 5 years
    private final String GovernanceConfig16 = "governance_config16.yaml";
    // Duration based
    private final String GovernanceConfig17 = "governance_config17.yaml";
    // Recurring Window
    private final String GovernanceConfig18 = "governance_config18.yaml";
    // Missing Environment
    private final String GovernanceConfig19 = "governance_config19.yaml";
    // Missing Service
    private final String GovernanceConfig20 = "governance_config20.yaml";
    // Exclusion filter
    private final String GovernanceConfig21 = "governance_config21.yaml";
  }

  private ArgumentCaptor<GovernanceConfig> captor = ArgumentCaptor.forClass(GovernanceConfig.class);

  @Before
  public void setup() {
    doReturn(ACCOUNT_ID).when(appService).getAccountIdByAppId(anyString());
    doReturn(Optional.of(anApplication().build()))
        .when(mockYamlHelper)
        .getApplicationIfPresent(anyString(), anyString());

    // YamlHandlerFactory
    doReturn(handler).when(mockYamlHandlerFactory).getYamlHandler(YamlType.GOVERNANCE_CONFIG, null);
    doReturn(timeRangeBasedFreezeConfigYamlHandler)
        .when(mockYamlHandlerFactory)
        .getYamlHandler(YamlType.GOVERNANCE_FREEZE_CONFIG, "TIME_RANGE_BASED_FREEZE_CONFIG");
    doReturn(customAppFilterYamlHandler)
        .when(mockYamlHandlerFactory)
        .getYamlHandler(YamlType.APPLICATION_FILTER, "CUSTOM");
    doReturn(allAppFilterYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(YamlType.APPLICATION_FILTER, "ALL");
    doReturn(customEnvFilterYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(YamlType.ENV_FILTER, "CUSTOM");
    doReturn(allEnvFilterYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(YamlType.ENV_FILTER, "ALL");
    doReturn(allProdEnvFilterYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(YamlType.ENV_FILTER, "ALL_PROD");
    doReturn(allNonProdEnvFilterYamlHandler)
        .when(mockYamlHandlerFactory)
        .getYamlHandler(YamlType.ENV_FILTER, "ALL_NON_PROD");
    doReturn(serviceFilterYamlHandler).when(mockYamlHandlerFactory).getYamlHandler(YamlType.SERVICE_FILTER);

    // user group
    UserGroup userGroup = new UserGroup();
    userGroup.setName("Account Administrator");
    userGroup.setUuid("dIyaCXXVRp65abGOlN5Fmg");
    doReturn(userGroup).when(userGroupService).fetchUserGroupByName(any(), any());
    doReturn(userGroup).when(userGroupService).get(anyString());
    doReturn(userGroup).when(userGroupService).get(anyString(), anyString());
    doReturn(userGroup).when(userGroupService).getByName(eq(ACCOUNT_ID), eq(userGroup.getName()));

    // feature flag
    doReturn(true).when(featureFlagService).isEnabled(eq(FeatureName.NEW_DEPLOYMENT_FREEZE), anyString());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_CustomAppCustomEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    CustomEnvFilter environmentFilter = new CustomEnvFilter(
        EnvironmentFilterType.CUSTOM, testEnvs.stream().map(Environment::getUuid).collect(Collectors.toList()));
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(prodEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());
    doReturn(testEnvs).when(environmentService).getEnvironmentsFromIds(eq(ACCOUNT_ID), any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig1, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_ExclusionFilter() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    CustomEnvFilter environmentFilter =
        new CustomEnvFilter(EnvironmentFilterType.CUSTOM, Collections.singletonList("prod-id"));
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(prodEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());
    doReturn(testEnvs).when(environmentService).getEnvironmentsFromIds(eq(ACCOUNT_ID), any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));

    File yamlFile = new File(resourcePath + PATH_DELIMITER + validGovernanceConfigFiles.GovernanceConfig21);
    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Collections.singletonList(changeContext));
    verify(governanceConfigService).upsert(eq(ACCOUNT_ID), captor.capture());
    GovernanceConfig savedGovernanceConfig = captor.getValue();
    doReturn(savedGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));

    assertThat(savedGovernanceConfig).isNotNull();
    assertThat(applicationFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getExcludeAppSelections().get(0));
    assertThat(environmentFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()
                       .get(0)
                       .getExcludeAppSelections()
                       .get(0)
                       .getEnvSelection());
    assertThat(serviceFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()
                       .get(0)
                       .getExcludeAppSelections()
                       .get(0)
                       .getServiceSelection());

    yaml = handler.toYaml(savedGovernanceConfig, APP_ID);

    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);

    assertThat(yamlContent).isEqualTo(yamlString);

    GovernanceConfig retrievedGovernanceConfig = handler.get(ACCOUNT_ID, yamlFilePath);

    assertThat(retrievedGovernanceConfig).isNotNull();
    assertThat(retrievedGovernanceConfig.getUuid()).isEqualTo(savedGovernanceConfig.getUuid());
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_Expires() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    CustomEnvFilter environmentFilter = new CustomEnvFilter(
        EnvironmentFilterType.CUSTOM, testEnvs.stream().map(Environment::getUuid).collect(Collectors.toList()));
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(prodEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());
    doReturn(testEnvs).when(environmentService).getEnvironmentsFromIds(eq(ACCOUNT_ID), any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testUpsertForExpires(
        validGovernanceConfigFiles.GovernanceConfig16, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_DurationBasedExpires() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    CustomEnvFilter environmentFilter = new CustomEnvFilter(
        EnvironmentFilterType.CUSTOM, testEnvs.stream().map(Environment::getUuid).collect(Collectors.toList()));
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(prodEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());
    doReturn(testEnvs).when(environmentService).getEnvironmentsFromIds(eq(ACCOUNT_ID), any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testUpsertForExpires(
        validGovernanceConfigFiles.GovernanceConfig17, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_RecurringWindowExpires() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    CustomEnvFilter environmentFilter = new CustomEnvFilter(
        EnvironmentFilterType.CUSTOM, testEnvs.stream().map(Environment::getUuid).collect(Collectors.toList()));
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(prodEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());
    doReturn(testEnvs).when(environmentService).getEnvironmentsFromIds(eq(ACCOUNT_ID), any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testUpsertForRecurringWindowExpires(
        validGovernanceConfigFiles.GovernanceConfig18, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_CustomAppAllEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    AllEnvFilter environmentFilter = new AllEnvFilter(EnvironmentFilterType.ALL);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig3, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_CustomAppAllProdEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    AllProdEnvFilter environmentFilter = new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);

    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig4, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_CustomAppAllNonProdEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    AllNonProdEnvFilter environmentFilter = new AllNonProdEnvFilter(EnvironmentFilterType.ALL_NON_PROD);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig5, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_CustomAppAllProdEnv_WithEnvs() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    AllNonProdEnvFilter environmentFilter = new AllNonProdEnvFilter(EnvironmentFilterType.ALL_NON_PROD);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    CustomAppFilter applicationFilter = new CustomAppFilter(BlackoutWindowFilterType.CUSTOM, environmentFilter,
        Collections.singletonList(testApp.getUuid()), serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testUpsertGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig6, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_AllAppAllEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    AllEnvFilter environmentFilter = new AllEnvFilter(EnvironmentFilterType.ALL);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    AllAppFilter applicationFilter = new AllAppFilter(BlackoutWindowFilterType.ALL, environmentFilter, serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig7, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_AllAppAllProdEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    AllProdEnvFilter environmentFilter = new AllProdEnvFilter(EnvironmentFilterType.ALL_PROD);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    AllAppFilter applicationFilter = new AllAppFilter(BlackoutWindowFilterType.ALL, environmentFilter, serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig8, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_AllAppAllNonProdEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    AllNonProdEnvFilter environmentFilter = new AllNonProdEnvFilter(EnvironmentFilterType.ALL_NON_PROD);
    ServiceFilter serviceFilter = new ServiceFilter(ServiceFilterType.ALL, null);
    AllAppFilter applicationFilter = new AllAppFilter(BlackoutWindowFilterType.ALL, environmentFilter, serviceFilter);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    testCRUDGovernanceConfig(
        validGovernanceConfigFiles.GovernanceConfig9, applicationFilter, environmentFilter, serviceFilter);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_CustomAppCustomEnv_Error1() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    Application sampleApp =
        Application.Builder.anApplication().name("sample").uuid("APP_ID2").environments(testEnvs).build();

    doReturn(testApp).when(appService).getAppByName(anyString(), eq("test"));
    doReturn(sampleApp).when(appService).getAppByName(anyString(), eq("sample"));

    assertThatThrownBy(() -> testCRUDGovernanceConfig(validGovernanceConfigFiles.GovernanceConfig2, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Application filter should have exactly one app when environment filter type is CUSTOM");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_AllAppCustomEnv_Error() throws IOException, HarnessException {
    assertThatThrownBy(() -> testCRUDGovernanceConfig(validGovernanceConfigFiles.GovernanceConfig10, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("CUSTOM Environments can be selected with only selecting 1 app");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_unknownApp() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    doReturn(null).when(appService).getAppByName(anyString(), anyString());
    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();
    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));

    assertThatThrownBy(() -> testCRUDGovernanceConfig(validGovernanceConfigFiles.GovernanceConfig11, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid App name: testUnknown");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_unknownEnv() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(null).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();
    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));

    assertThatThrownBy(() -> testCRUDGovernanceConfig(validGovernanceConfigFiles.GovernanceConfig12, null, null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid Environment: prodUnknown");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_throwErrorWithoutName() throws IOException, HarnessException {
    assertThatThrownBy(() -> testCRUDGovernanceConfig(validGovernanceConfigFiles.GovernanceConfig13, null, null, null))
        .isInstanceOf(GeneralException.class)
        .hasMessage("Name is required");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_StartTimeLessThanEndTime() throws IOException, HarnessException {
    List<Environment> testEnvs = new ArrayList<>();
    Environment prodEnv = anEnvironment().name("prod").uuid("prod-id").build();
    Environment qaEnv = anEnvironment().name("qa").uuid("qa-id").build();
    testEnvs.add(prodEnv);
    testEnvs.add(qaEnv);

    Application testApp = Application.Builder.anApplication().name("test").uuid(APP_ID).environments(testEnvs).build();
    CustomEnvFilter environmentFilter = new CustomEnvFilter(
        EnvironmentFilterType.CUSTOM, testEnvs.stream().map(Environment::getUuid).collect(Collectors.toList()));
    CustomAppFilter applicationFilter = new CustomAppFilter(
        BlackoutWindowFilterType.CUSTOM, environmentFilter, Collections.singletonList(testApp.getUuid()), null);

    doReturn(testApp).when(appService).getAppByName(anyString(), anyString());
    doReturn(prodEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("prod"));
    doReturn(qaEnv).when(environmentService).getEnvironmentByName(eq(APP_ID), eq("qa"));

    doReturn(Collections.singletonList(testApp)).when(appService).getAppsByIds(any());
    doReturn(testEnvs.stream().map(Environment::getName).collect(Collectors.toList()))
        .when(environmentService)
        .getNames(eq(ACCOUNT_ID), any());

    GovernanceConfig oldGovernanceConfig = GovernanceConfig.builder()
                                               .accountId(ACCOUNT_ID)
                                               .deploymentFreeze(false)
                                               .timeRangeBasedFreezeConfigs(null)
                                               .build();

    doReturn(oldGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    assertThatThrownBy(() -> testCRUDGovernanceConfig(validGovernanceConfigFiles.GovernanceConfig15, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Start Time should be strictly smaller than End Time");
  }
  @SneakyThrows
  @Test
  @Owner(developers = ATHARVA)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_MissingEnv() {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + validGovernanceConfigFiles.GovernanceConfig19);
    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);
    assertThatThrownBy(() -> handler.upsertFromYaml(changeContext, Arrays.asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Environment Selection Missing in Yaml");
  }

  @SneakyThrows
  @Test
  @Owner(developers = ATHARVA)
  @Category(UnitTests.class)
  public void testCRUDGovernanceConfig_MissingService() {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + validGovernanceConfigFiles.GovernanceConfig20);
    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);
    assertThatThrownBy(() -> handler.upsertFromYaml(changeContext, Arrays.asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Service Selection Missing in Yaml");
  }

  private void testCRUDGovernanceConfig(String yamlFileName, ApplicationFilter applicationFilter,
      EnvironmentFilter environmentFilter, ServiceFilter serviceFilter) throws IOException, HarnessException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    verify(governanceConfigService).upsert(eq(ACCOUNT_ID), captor.capture());
    GovernanceConfig savedGovernanceConfig = captor.getValue();
    doReturn(savedGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));

    assertThat(savedGovernanceConfig).isNotNull();
    assertThat(applicationFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0));
    assertThat(environmentFilter)
        .isEqualTo(
            savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).getEnvSelection());
    assertThat(serviceFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()
                       .get(0)
                       .getAppSelections()
                       .get(0)
                       .getServiceSelection());

    yaml = handler.toYaml(savedGovernanceConfig, APP_ID);

    assertThat(yaml).isNotNull();
    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);

    assertThat(yamlContent).isEqualTo(yamlString);

    doReturn(savedGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    GovernanceConfig retrievedGovernanceConfig = handler.get(ACCOUNT_ID, yamlFilePath);

    assertThat(retrievedGovernanceConfig).isNotNull();
    assertThat(retrievedGovernanceConfig.getUuid()).isEqualTo(savedGovernanceConfig.getUuid());
  }

  private void testUpsertGovernanceConfig(String yamlFileName, ApplicationFilter applicationFilter,
      EnvironmentFilter environmentFilter, ServiceFilter serviceFilter) throws IOException, HarnessException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    verify(governanceConfigService).upsert(eq(ACCOUNT_ID), captor.capture());
    GovernanceConfig savedGovernanceConfig = captor.getValue();
    doReturn(savedGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));

    assertThat(savedGovernanceConfig).isNotNull();
    assertThat(applicationFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0));
    assertThat(environmentFilter)
        .isEqualTo(
            savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).getEnvSelection());
    assertThat(serviceFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()
                       .get(0)
                       .getAppSelections()
                       .get(0)
                       .getServiceSelection());
  }

  private void testUpsertForExpires(String yamlFileName, ApplicationFilter applicationFilter,
      EnvironmentFilter environmentFilter, ServiceFilter serviceFilter) throws IOException, HarnessException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    verify(governanceConfigService).upsert(eq(ACCOUNT_ID), captor.capture());
    GovernanceConfig savedGovernanceConfig = captor.getValue();
    doReturn(savedGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    assertThat(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getTimeRange().isExpires())
        .isEqualTo(false);

    assertThat(savedGovernanceConfig).isNotNull();
    assertThat(applicationFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0));
    assertThat(environmentFilter)
        .isEqualTo(
            savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).getEnvSelection());
    assertThat(serviceFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()
                       .get(0)
                       .getAppSelections()
                       .get(0)
                       .getServiceSelection());
  }

  private void testUpsertForRecurringWindowExpires(String yamlFileName, ApplicationFilter applicationFilter,
      EnvironmentFilter environmentFilter, ServiceFilter serviceFilter) throws IOException, HarnessException {
    File yamlFile = null;
    yamlFile = new File(resourcePath + PATH_DELIMITER + yamlFileName);

    assertThat(yamlFile).isNotNull();
    String yamlString = FileUtils.readFileToString(yamlFile, "UTF-8");
    ChangeContext<GovernanceConfig.Yaml> changeContext = getChangeContext(yamlString);
    GovernanceConfig.Yaml yaml = (GovernanceConfig.Yaml) getYaml(yamlString, GovernanceConfig.Yaml.class);
    changeContext.setYaml(yaml);

    handler.upsertFromYaml(changeContext, Arrays.asList(changeContext));
    verify(governanceConfigService).upsert(eq(ACCOUNT_ID), captor.capture());
    GovernanceConfig savedGovernanceConfig = captor.getValue();
    doReturn(savedGovernanceConfig).when(governanceConfigService).get(eq(ACCOUNT_ID));
    assertThat(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getTimeRange().isExpires())
        .isEqualTo(true);

    assertThat(savedGovernanceConfig).isNotNull();
    assertThat(applicationFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0));
    assertThat(environmentFilter)
        .isEqualTo(
            savedGovernanceConfig.getTimeRangeBasedFreezeConfigs().get(0).getAppSelections().get(0).getEnvSelection());
    assertThat(serviceFilter)
        .isEqualTo(savedGovernanceConfig.getTimeRangeBasedFreezeConfigs()
                       .get(0)
                       .getAppSelections()
                       .get(0)
                       .getServiceSelection());
  }

  private ChangeContext<GovernanceConfig.Yaml> getChangeContext(String validYamlContent) {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<GovernanceConfig.Yaml> changeContext = new ChangeContext<>();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.GOVERNANCE_CONFIG);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }
}
