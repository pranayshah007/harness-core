/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.FeatureName.SPG_ALLOW_DISABLE_TRIGGERS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LALIT;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.VINICIUS;

import static software.wings.api.DeploymentType.CUSTOM;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.ApprovalNotification.Builder.anApprovalNotification;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.security.UserThreadLocal.userGuard;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.NOTIFICATION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Event.Type;
import software.wings.beans.Notification;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.StringValue;
import software.wings.beans.StringValue.Builder;
import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.scheduler.ServiceJobScheduler;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppContainerService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.Key;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * The type App service test.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
public class AppServiceTest extends WingsBaseTest {
  /**
   * The Query.
   */
  @Mock Query<Application> query;

  /**
   * The End.
   */
  @Mock FieldEnd end;
  /**
   * The Update operations.
   */
  @Mock UpdateOperations<Application> updateOperations;

  @Mock private WingsPersistence wingsPersistence;
  @Mock private YamlPushService yamlPushService;

  @Inject @InjectMocks AppService appService;
  @Mock private LimitCheckerFactory limitCheckerFactory;

  @Mock private AlertService alertService;
  @Mock private AppContainerService appContainerService;
  @Mock private ArtifactService artifactService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private EnvironmentService environmentService;
  @Mock private InstanceService instanceService;
  @Mock private NotificationService notificationService;
  @Mock private PipelineService pipelineService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SettingsService settingsService;
  @Mock private TriggerService triggerService;
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private YamlGitService yamlGitService;
  @Mock private TemplateService templateService;
  @Mock private UsageRestrictionsService usageRestrictionsService;
  @Mock private FeatureFlagService featureFlagService;

  @Mock private BackgroundJobScheduler backgroundJobScheduler;
  @Mock private ServiceJobScheduler serviceJobScheduler;

  @Inject @InjectMocks ResourceLookupService resourceLookupService;

  /**
   * Sets up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    when(wingsPersistence.createQuery(Application.class)).thenReturn(query);
    when(wingsPersistence.createQuery(Application.class, excludeAuthority)).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(Application.class)).thenReturn(updateOperations);
    when(query.filter(any(), any())).thenReturn(query);
    when(updateOperations.set(any(), any())).thenReturn(updateOperations);
    when(updateOperations.addToSet(any(), any())).thenReturn(updateOperations);
    when(updateOperations.removeAll(any(), any(Service.class))).thenReturn(updateOperations);
  }

  /**
   * Should save application.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSaveApplication() {
    Application app = anApplication().name("AppA").accountId(ACCOUNT_ID).description("Description1").build();
    Application savedApp =
        anApplication().uuid(APP_ID).accountId("ACCOUNT_ID").name("AppA").description("Description1").build();
    when(wingsPersistence.saveAndGet(eq(Application.class), any(Application.class))).thenReturn(savedApp);
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(savedApp);
    when(notificationService.list(any(PageRequest.class))).thenReturn(new PageResponse<Notification>());
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.APP_DYNAMICS.name()))
        .thenReturn(Lists.newArrayList(aSettingAttribute().withUuid("id").build()));
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    appService.save(app);
    ArgumentCaptor<Application> calledApp = ArgumentCaptor.forClass(Application.class);
    verify(wingsPersistence).saveAndGet(eq(Application.class), calledApp.capture());
    Application calledAppValue = calledApp.getValue();
    assertThat(calledAppValue)
        .isNotNull()
        .extracting("accountId", "name", "description")
        .containsExactly(app.getAccountId(), app.getName(), app.getDescription());
    assertThat(calledAppValue.getKeywords())
        .isNotNull()
        .contains(app.getName().toLowerCase(), app.getDescription().toLowerCase());

    verify(settingsService).createDefaultApplicationSettings(APP_ID, "ACCOUNT_ID", false);
    verify(notificationService).sendNotificationAsync(any(Notification.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotSaveApplicationWithEmptySpaces() {
    Application app = anApplication().name("    ").accountId(ACCOUNT_ID).description("Description1").build();
    Application savedApp =
        anApplication().uuid(APP_ID).accountId("ACCOUNT_ID").name("  ").description("Description1").build();
    when(wingsPersistence.saveAndGet(eq(Application.class), any(Application.class))).thenReturn(savedApp);
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(savedApp);
    when(settingsService.getGlobalSettingAttributesByType(ACCOUNT_ID, SettingVariableTypes.APP_DYNAMICS.name()))
        .thenReturn(Lists.newArrayList(aSettingAttribute().withUuid("id").build()));
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    appService.save(app);
  }

  /**
   * Should list.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListApplication() {
    Application application = anApplication().build();
    PageResponse<Application> pageResponse = new PageResponse<>();
    PageRequest<Application> pageRequest = new PageRequest<>();
    pageResponse.setResponse(asList(application));
    Query mockQuery = mock(Query.class);
    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(wingsPersistence.createQuery(YamlGitConfig.class)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.in(any())).thenReturn(mockQuery);
    when(mockQuery.asList()).thenReturn(Collections.emptyList());
    when(wingsPersistence.query(Application.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<Application> applications = appService.list(pageRequest);
    assertThat(applications).containsAll(asList(application));
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldListApplicationWithDetails() {
    Application application = anApplication().build();
    PageResponse<Application> pageResponse = new PageResponse<>();
    PageRequest<Application> pageRequest = new PageRequest<>();
    pageResponse.setResponse(asList(application));
    Query mockQuery = mock(Query.class);
    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(wingsPersistence.createQuery(YamlGitConfig.class)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.in(any())).thenReturn(mockQuery);
    when(mockQuery.asList()).thenReturn(Collections.emptyList());
    when(wingsPersistence.query(Application.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<Application> applications = appService.list(pageRequest);
    assertThat(applications).containsAll(asList(application));
  }

  /**
   * Should get application.
   */
  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetApplicationWithDetails() {
    PageResponse<Notification> notificationPageResponse = new PageResponse<>();
    notificationPageResponse.add(anApprovalNotification().withAppId(APP_ID).withUuid(NOTIFICATION_ID).build());
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(anApplication().uuid(APP_ID).build());
    Application application = appService.get(APP_ID, true);
    verify(wingsPersistence).get(Application.class, APP_ID);
    assertThat(application).isNotNull();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetApplicationWithDefaults() {
    when(wingsPersistence.get(Application.class, APP_ID))
        .thenReturn(anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).build());
    List<SettingAttribute> settingAttributes = asList(aSettingAttribute()
                                                          .withName("NAME")
                                                          .withAccountId("ACCOUNT_ID")
                                                          .withValue(Builder.aStringValue().build())
                                                          .build(),
        aSettingAttribute()
            .withName("NAME2")
            .withAccountId("ACCOUNT_ID")
            .withValue(Builder.aStringValue().withValue("VALUE").build())
            .build());

    when(settingsService.listAppDefaults(ACCOUNT_ID, APP_ID))
        .thenReturn(settingAttributes.stream().collect(Collectors.toMap(SettingAttribute::getName,
            settingAttribute
            -> Optional.ofNullable(((StringValue) settingAttribute.getValue()).getValue()).orElse(""),
            (a, b) -> b)));

    Application application = appService.getApplicationWithDefaults(APP_ID);
    assertThat(application).isNotNull();
    assertThat(application.getDefaults()).isNotEmpty().containsKeys("NAME", "NAME2");
    assertThat(application.getDefaults()).isNotEmpty().containsValues("", "VALUE");
    verify(wingsPersistence).get(Application.class, APP_ID);
    verify(settingsService).listAppDefaults(ACCOUNT_ID, APP_ID);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldReturnTrueForExistingApplicationInExistApi() {
    when(query.getKey()).thenReturn(new Key<>(Application.class, "applications", APP_ID));
    assertThat(appService.exist(APP_ID)).isTrue();
    verify(query).filter(ID_KEY, APP_ID);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForNonExistentApplicationGet() {
    assertThatThrownBy(() -> appService.get("NON_EXISTENT_APP_ID"))
        .isInstanceOf(WingsException.class)
        .hasMessage(INVALID_ARGUMENT.name());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotThrowExceptionForNonExistentApplicationDelete() {
    assertThatCode(() -> {
      when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
      appService.delete("NON_EXISTENT_APP_ID");
    }).doesNotThrowAnyException();
  }

  /**
   * Should update.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUpdateApplication() throws IOException {
    try (UserThreadLocal.Guard guard = userGuard(null)) {
      Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
      when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);
      appService.update(
          anApplication().uuid(APP_ID).name("App_Name").description("Description").accountId(ACCOUNT_ID).build());
      verify(query).filter(ID_KEY, APP_ID);
      verify(updateOperations).set("name", "App_Name");
      verify(updateOperations).set("description", "Description");
      verify(updateOperations)
          .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
      verify(wingsPersistence).update(query, updateOperations);
      verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
      verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldUpdateApplicationWithIsManualTriggerAuthorized() {
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.update(anApplication()
                          .uuid(APP_ID)
                          .name("App_Name")
                          .description("Description")
                          .isManualTriggerAuthorized(true)
                          .accountId(ACCOUNT_ID)
                          .build());
    verify(query).filter(ID_KEY, APP_ID);
    verify(updateOperations).set("name", "App_Name");
    verify(updateOperations).set("description", "Description");
    verify(updateOperations)
        .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
    verify(updateOperations).set("isManualTriggerAuthorized", true);
    verify(wingsPersistence).update(query, updateOperations);
    verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldUpdateApplicationWithoutIsManualTriggerAuthorized() {
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, ACCOUNT_ID)).thenReturn(true);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.update(
        anApplication().uuid(APP_ID).name("App_Name").description("Description").accountId(ACCOUNT_ID).build());
    verify(query).filter(ID_KEY, APP_ID);
    verify(updateOperations).set("name", "App_Name");
    verify(updateOperations).set("description", "Description");
    verify(updateOperations)
        .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
    verify(updateOperations, never()).set("isManualTriggerAuthorized", true);
    verify(wingsPersistence).update(query, updateOperations);
    verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void shouldUpdateApplicationWithAreWebHookSecretsMandated() {
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.update(anApplication()
                          .uuid(APP_ID)
                          .name("App_Name")
                          .description("Description")
                          .isManualTriggerAuthorized(true)
                          .areWebHookSecretsMandated(true)
                          .accountId(ACCOUNT_ID)
                          .build());
    verify(query).filter(ID_KEY, APP_ID);
    verify(updateOperations).set("name", "App_Name");
    verify(updateOperations).set("description", "Description");
    verify(updateOperations)
        .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
    verify(updateOperations).set("areWebHookSecretsMandated", true);
    verify(wingsPersistence).update(query, updateOperations);
    verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
  }

  @Test
  @Owner(developers = LALIT)
  @Category(UnitTests.class)
  public void shouldUpdateApplicationWithoutAreWebHookSecretsMandated() {
    when(featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, ACCOUNT_ID)).thenReturn(true);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.update(
        anApplication().uuid(APP_ID).name("App_Name").description("Description").accountId(ACCOUNT_ID).build());
    verify(query).filter(ID_KEY, APP_ID);
    verify(updateOperations).set("name", "App_Name");
    verify(updateOperations).set("description", "Description");
    verify(updateOperations)
        .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
    verify(updateOperations, never()).set("areWebHookSecretsMandated", true);
    verify(wingsPersistence).update(query, updateOperations);
    verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldUpdateApplicationWithDisableTriggers() {
    when(featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, ACCOUNT_ID)).thenReturn(true);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.update(anApplication()
                          .uuid(APP_ID)
                          .name("App_Name")
                          .description("Description")
                          .isManualTriggerAuthorized(true)
                          .disableTriggers(true)
                          .accountId(ACCOUNT_ID)
                          .build());
    verify(query).filter(ID_KEY, APP_ID);
    verify(updateOperations).set("name", "App_Name");
    verify(updateOperations).set("description", "Description");
    verify(updateOperations)
        .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
    verify(updateOperations).set("disableTriggers", true);
    verify(wingsPersistence).update(query, updateOperations);
    verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void shouldUpdateApplicationWithoutDisableTriggers() {
    when(featureFlagService.isEnabled(SPG_ALLOW_DISABLE_TRIGGERS, ACCOUNT_ID)).thenReturn(true);
    Application application = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.update(
        anApplication().uuid(APP_ID).name("App_Name").description("Description").accountId(ACCOUNT_ID).build());
    verify(query).filter(ID_KEY, APP_ID);
    verify(updateOperations).set("name", "App_Name");
    verify(updateOperations).set("description", "Description");
    verify(updateOperations)
        .set("keywords", new HashSet<>(asList("App_Name".toLowerCase(), "Description".toLowerCase())));
    verify(updateOperations, never()).set("disableTriggers", true);
    verify(wingsPersistence).update(query, updateOperations);
    verify(wingsPersistence, times(2)).get(Application.class, APP_ID);
    verify(yamlPushService).pushYamlChangeSet(ACCOUNT_ID, application, application, Type.UPDATE, false, true);
  }

  /**
   * Should delete.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDeleteApplication() {
    final String UUID = RandomStringUtils.randomAlphanumeric(32);

    Command command = aCommand().withName("Install").withAccountId(ACCOUNT_ID).withOriginEntityId(UUID).build();
    command.setAppId(APP_ID);

    ServiceCommand serviceCommand = aServiceCommand()
                                        .withName("Install")
                                        .withAccountId(ACCOUNT_ID)
                                        .withServiceId(SERVICE_ID)
                                        .withAppId(APP_ID)
                                        .withUuid(UUID)
                                        .build();

    List<ServiceCommand> serviceCommands = new ArrayList<>();
    serviceCommands.add(serviceCommand);

    Service service = Service.builder()
                          .name("custom-service")
                          .appId(APP_ID)
                          .uuid(SERVICE_ID)
                          .deploymentType(CUSTOM)
                          .deploymentTypeTemplateId(TEMPLATE_ID)
                          .serviceCommands(serviceCommands)
                          .build();

    List<Service> services = new ArrayList<>();
    services.add(service);

    Application application = anApplication()
                                  .uuid(APP_ID)
                                  .name("APP_NAME")
                                  .accountId("some-account-id-" + AppServiceTest.class.getSimpleName())
                                  .services(services)
                                  .build();

    when(wingsPersistence.delete(any(), any())).thenReturn(true);
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(application);

    appService.delete(APP_ID);
    InOrder inOrder = inOrder(wingsPersistence, notificationService, serviceResourceService, environmentService,
        appContainerService, artifactService, artifactStreamService, instanceService, workflowService, pipelineService,
        alertService, triggerService, templateService);
    inOrder.verify(wingsPersistence).delete(Application.class, APP_ID);
    inOrder.verify(notificationService).sendNotificationAsync(any(Notification.class));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjects() {
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(null);
    appService.pruneDescendingEntities(APP_ID);
    InOrder inOrder = inOrder(wingsPersistence, notificationService, serviceResourceService, settingsService,
        environmentService, appContainerService, artifactService, artifactStreamService, instanceService,
        workflowService, pipelineService, alertService, triggerService, templateService);

    inOrder.verify(alertService).pruneByApplication(APP_ID);
    inOrder.verify(environmentService).pruneByApplication(APP_ID);
    inOrder.verify(instanceService).pruneByApplication(APP_ID);
    inOrder.verify(notificationService).pruneByApplication(APP_ID);
    inOrder.verify(pipelineService).pruneByApplication(APP_ID);
    inOrder.verify(serviceResourceService).pruneByApplication(APP_ID);
    inOrder.verify(settingsService).pruneByApplication(APP_ID);
    inOrder.verify(triggerService).pruneByApplication(APP_ID);
    inOrder.verify(workflowService).pruneByApplication(APP_ID);
    inOrder.verify(templateService).pruneByApplication(APP_ID);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldPruneDescendingObjectSomeFailed() {
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(null);
    doThrow(new WingsException("Forced exception")).when(pipelineService).pruneByApplication(APP_ID);

    assertThatThrownBy(() -> appService.pruneDescendingEntities(APP_ID)).isInstanceOf(WingsException.class);

    InOrder inOrder = inOrder(wingsPersistence, notificationService, serviceResourceService, environmentService,
        appContainerService, artifactService, artifactStreamService, instanceService, workflowService, pipelineService,
        alertService, triggerService, templateService);

    inOrder.verify(alertService).pruneByApplication(APP_ID);
    inOrder.verify(environmentService).pruneByApplication(APP_ID);
    inOrder.verify(instanceService).pruneByApplication(APP_ID);
    inOrder.verify(notificationService).pruneByApplication(APP_ID);
    inOrder.verify(pipelineService).pruneByApplication(APP_ID);
    inOrder.verify(serviceResourceService).pruneByApplication(APP_ID);
    inOrder.verify(triggerService).pruneByApplication(APP_ID);
    inOrder.verify(workflowService).pruneByApplication(APP_ID);
    inOrder.verify(templateService).pruneByApplication(APP_ID);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnNullIfAppIsNotFoundById() {
    when(wingsPersistence.get(Application.class, APP_ID)).thenReturn(null);
    String accountIdByAppId = appService.getAccountIdByAppId(APP_ID);
    assertThat(accountIdByAppId).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldCheckForRunningExecutionsBeforeDelete() {
    when(wingsPersistence.get(Application.class, APP_ID))
        .thenReturn(anApplication().name(APP_NAME).uuid(APP_ID).build());
    when(workflowExecutionService.runningExecutionsForApplication(APP_ID))
        .thenReturn(asList(PIPELINE_EXECUTION_ID, WORKFLOW_EXECUTION_ID));
    assertThatThrownBy(() -> appService.delete(APP_ID, false)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldPruneApp() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(wingsPersistence.get(Application.class, APP_ID))
        .thenReturn(anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build());
    when(workflowExecutionService.runningExecutionsForApplication(APP_ID)).thenReturn(null);
    when(wingsPersistence.delete(Application.class, APP_ID)).thenReturn(true);
    appService.delete(APP_ID, false);
    verify(yamlPushService).pushYamlChangeSet(anyString(), any(), any(), any(), anyBoolean(), anyBoolean());
    verify(usageRestrictionsService).removeAppEnvReferences(any(), any(), any());
    verify(wingsPersistence).delete(any(), anyString());
  }
}
