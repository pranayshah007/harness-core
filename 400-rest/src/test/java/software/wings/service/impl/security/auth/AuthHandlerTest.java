/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.security.EnvFilter.FilterType.NON_PROD;
import static software.wings.security.EnvFilter.FilterType.PROD;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ALL_APP_ENTITIES;
import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CREATE_CUSTOM_DASHBOARDS;
import static software.wings.security.PermissionAttribute.PermissionType.DEPLOYMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.PermissionType.HIDE_NEXTGEN_BUTTON;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ACCOUNT_DEFAULTS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ALERT_NOTIFICATION_RULES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_API_KEYS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATION_STACKS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_AUTHENTICATION_SETTINGS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CLOUD_PROVIDERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONFIG_AS_CODE;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CUSTOM_DASHBOARDS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATE_PROFILES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DEPLOYMENT_FREEZES;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_IP_WHITELIST;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_PIPELINE_GOVERNANCE_STANDARDS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_RESTRICTED_ACCESS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRETS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SSH_AND_WINRM;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_TAGS;
import static software.wings.security.PermissionAttribute.PermissionType.PIPELINE;
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.TemplateExpression;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.Workflow;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.security.AccountPermissionSummary;
import software.wings.security.AppFilter;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.WorkflowFilter;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.WorkflowService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@OwnedBy(PL)
@TargetModule(HarnessModule.UNDEFINED)
public class AuthHandlerTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private EnvironmentService environmentService;
  @Mock private WorkflowService workflowService;
  @Mock private PipelineService pipelineService;

  @InjectMocks @Inject private AuthHandler authHandler;

  private List<PermissionType> accountPermissionTypes =
      asList(ACCOUNT_MANAGEMENT, MANAGE_APPLICATIONS, USER_PERMISSION_MANAGEMENT, TEMPLATE_MANAGEMENT);
  private List<Action> allActions = asList(
      Action.CREATE, Action.UPDATE, Action.READ, Action.DELETE, Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW);

  private List<String> appIds = asList(APP_ID, "appId1", "appId2", "appId3");
  private Service service1 = Service.builder().uuid(generateUuid()).appId(APP_ID).build();
  private Service service2 = Service.builder().uuid(generateUuid()).appId(APP_ID).build();

  private InfrastructureProvisioner infrastructureProvisioner =
      TerraformInfrastructureProvisioner.builder().appId(APP_ID).build();

  private Environment dev =
      anEnvironment().uuid(generateUuid()).appId(APP_ID).environmentType(EnvironmentType.NON_PROD).build();
  private Environment qa =
      anEnvironment().uuid(generateUuid()).appId(APP_ID).environmentType(EnvironmentType.NON_PROD).build();
  private Environment prod =
      anEnvironment().uuid(generateUuid()).appId(APP_ID).environmentType(EnvironmentType.PROD).build();
  private Environment dr =
      anEnvironment().uuid(generateUuid()).appId(APP_ID).environmentType(EnvironmentType.PROD).build();

  private Workflow workflow1 = aWorkflow().uuid(generateUuid()).appId(APP_ID).envId(dev.getUuid()).build();
  private Workflow workflow2 = aWorkflow().uuid(generateUuid()).appId(APP_ID).envId(qa.getUuid()).build();
  private Workflow workflow3 = aWorkflow().uuid(generateUuid()).appId(APP_ID).envId(prod.getUuid()).build();
  private Workflow workflow4 = aWorkflow()
                                   .uuid(generateUuid())
                                   .appId(APP_ID)
                                   .envId(null)
                                   .templateExpressions(Collections.singletonList(
                                       TemplateExpression.builder().fieldName("envId").expression("${envVar}").build()))
                                   .build();
  private Workflow buildWorkflow = aWorkflow().uuid(generateUuid()).appId(APP_ID).envId(null).build();

  private Pipeline pipeline0 = Pipeline.builder().uuid(generateUuid()).appId(APP_ID).build();

  private Pipeline pipeline1 =
      Pipeline.builder()
          .uuid(generateUuid())
          .appId(APP_ID)
          .pipelineStages(asList(
              PipelineStage.builder()
                  .pipelineStageElements(asList(PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of("workflowId", workflow1.getUuid()))
                                                    .type(ENV_STATE.name())
                                                    .build()))
                  .build(),
              PipelineStage.builder()
                  .pipelineStageElements(asList(PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of("workflowId", workflow3.getUuid()))
                                                    .type(ENV_STATE.name())
                                                    .build()))
                  .build()))
          .build();

  private Pipeline pipeline2 =
      Pipeline.builder()
          .uuid(generateUuid())
          .appId(APP_ID)
          .pipelineStages(asList(
              PipelineStage.builder()
                  .pipelineStageElements(asList(PipelineStageElement.builder()
                                                    .properties(ImmutableMap.of("workflowId", workflow3.getUuid()))
                                                    .type(ENV_STATE.name())
                                                    .build()))
                  .build()))
          .build();

  private Pipeline pipeline3 = Pipeline.builder()
                                   .uuid(generateUuid())
                                   .appId(APP_ID)
                                   .pipelineStages(Collections.singletonList(
                                       PipelineStage.builder()
                                           .pipelineStageElements(Collections.singletonList(
                                               PipelineStageElement.builder()
                                                   .properties(ImmutableMap.of("workflowId", workflow4.getUuid()))
                                                   .type(ENV_STATE.name())
                                                   .workflowVariables(ImmutableMap.of("envVar", prod.getUuid()))
                                                   .build()))
                                           .build()))
                                   .build();

  private Pipeline pipeline4 = Pipeline.builder()
                                   .uuid(generateUuid())
                                   .appId(APP_ID)
                                   .pipelineStages(Collections.singletonList(
                                       PipelineStage.builder()
                                           .pipelineStageElements(Collections.singletonList(
                                               PipelineStageElement.builder()
                                                   .properties(ImmutableMap.of("workflowId", workflow4.getUuid()))
                                                   .type(ENV_STATE.name())
                                                   .workflowVariables(ImmutableMap.of("envVar", qa.getUuid()))
                                                   .build()))
                                           .build()))
                                   .build();

  private Pipeline pipeline5 = Pipeline.builder()
                                   .uuid(generateUuid())
                                   .appId(APP_ID)
                                   .pipelineStages(Collections.singletonList(
                                       PipelineStage.builder()
                                           .pipelineStageElements(Collections.singletonList(
                                               PipelineStageElement.builder()
                                                   .properties(ImmutableMap.of("workflowId", workflow4.getUuid()))
                                                   .type(ENV_STATE.name())
                                                   .workflowVariables(ImmutableMap.of("envVar", "${newEnvVar}"))
                                                   .build()))
                                           .build()))
                                   .build();

  private Pipeline approvalPipeline =
      Pipeline.builder()
          .uuid(generateUuid())
          .appId(APP_ID)
          .pipelineStages(
              asList(PipelineStage.builder()
                         .pipelineStageElements(asList(PipelineStageElement.builder().type(APPROVAL.name()).build()))
                         .build()))
          .build();

  private Pipeline buildPipeline =
      Pipeline.builder()
          .uuid(generateUuid())
          .appId(APP_ID)
          .pipelineStages(
              asList(PipelineStage.builder()
                         .pipelineStageElements(asList(PipelineStageElement.builder().type(APPROVAL.name()).build(),
                             PipelineStageElement.builder()
                                 .properties(ImmutableMap.of("workflowId", buildWorkflow.getUuid()))
                                 .type(ENV_STATE.name())
                                 .build()))
                         .build()))
          .build();

  private AccountPermissions accountPermissions =
      AccountPermissions.builder().permissions(new HashSet<>(accountPermissionTypes)).build();
  private AppPermission allAppPermission = AppPermission.builder()
                                               .permissionType(ALL_APP_ENTITIES)
                                               .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                               .actions(new HashSet<>(allActions))
                                               .build();

  @Before
  public void setUp() {
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, workflow1.getUuid())).thenReturn(workflow1);
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, workflow2.getUuid())).thenReturn(workflow2);
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, workflow3.getUuid())).thenReturn(workflow3);
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, workflow4.getUuid())).thenReturn(workflow4);
    when(workflowService.readWorkflowWithoutOrchestration(APP_ID, buildWorkflow.getUuid())).thenReturn(buildWorkflow);
    pipeline0.setPipelineStages(null);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void getAllAccountPermissions() {
    Set<PermissionType> permissionTypes = Sets.newHashSet(USER_PERMISSION_MANAGEMENT, ACCOUNT_MANAGEMENT,
        MANAGE_APPLICATIONS, TEMPLATE_MANAGEMENT, USER_PERMISSION_READ, AUDIT_VIEWER, MANAGE_TAGS,
        MANAGE_ACCOUNT_DEFAULTS, CE_ADMIN, CE_VIEWER, MANAGE_CLOUD_PROVIDERS, MANAGE_CONNECTORS,
        MANAGE_APPLICATION_STACKS, MANAGE_DELEGATES, MANAGE_ALERT_NOTIFICATION_RULES, MANAGE_DELEGATE_PROFILES,
        MANAGE_CONFIG_AS_CODE, MANAGE_SECRETS, MANAGE_SECRET_MANAGERS, MANAGE_AUTHENTICATION_SETTINGS,
        MANAGE_IP_WHITELIST, MANAGE_DEPLOYMENT_FREEZES, MANAGE_PIPELINE_GOVERNANCE_STANDARDS, MANAGE_CUSTOM_DASHBOARDS,
        CREATE_CUSTOM_DASHBOARDS, MANAGE_SSH_AND_WINRM, MANAGE_RESTRICTED_ACCESS, HIDE_NEXTGEN_BUTTON);

    Set<PermissionType> accountPermissions = authHandler.getAllAccountPermissions();
    permissionTypes.forEach(permissionType -> assertThat(accountPermissions.contains(permissionType)).isTrue());
    assertThat(accountPermissions.containsAll(permissionTypes)).isTrue();
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void getDefaultEnabledAccountPermissions() {
    Set<PermissionType> permissionTypes = Sets.newHashSet(USER_PERMISSION_MANAGEMENT, ACCOUNT_MANAGEMENT,
        MANAGE_APPLICATIONS, TEMPLATE_MANAGEMENT, USER_PERMISSION_READ, AUDIT_VIEWER, MANAGE_TAGS,
        MANAGE_ACCOUNT_DEFAULTS, CE_ADMIN, CE_VIEWER, MANAGE_CLOUD_PROVIDERS, MANAGE_CONNECTORS,
        MANAGE_APPLICATION_STACKS, MANAGE_DELEGATES, MANAGE_ALERT_NOTIFICATION_RULES, MANAGE_DELEGATE_PROFILES,
        MANAGE_CONFIG_AS_CODE, MANAGE_SECRETS, MANAGE_SECRET_MANAGERS, MANAGE_AUTHENTICATION_SETTINGS,
        MANAGE_IP_WHITELIST, MANAGE_DEPLOYMENT_FREEZES, MANAGE_PIPELINE_GOVERNANCE_STANDARDS, MANAGE_CUSTOM_DASHBOARDS,
        CREATE_CUSTOM_DASHBOARDS, MANAGE_SSH_AND_WINRM, MANAGE_RESTRICTED_ACCESS, MANAGE_API_KEYS);

    Set<PermissionType> accountPermissions = authHandler.getDefaultEnabledAccountPermissions();
    permissionTypes.forEach(permissionType -> assertThat(accountPermissions.contains(permissionType)).isTrue());
    assertThat(accountPermissions.containsAll(permissionTypes)).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldWorkForAccountAdministratorFirstTime() {
    setupForAllApp();

    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(Collections.emptyList());
    List<UserGroup> userGroups =
        Collections.singletonList(UserGroup.builder()
                                      .accountId(ACCOUNT_ID)
                                      .accountPermissions(accountPermissions)
                                      .appPermissions(new HashSet<>(Collections.singletonList(allAppPermission)))
                                      .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary).isNotNull();
    assertThat(accountPermissionSummary.getPermissions())
        .isNotNull()
        .hasSize(4)
        .hasSameElementsAs(accountPermissionTypes);

    assertThat(isEmpty(userPermissionInfo.getAppPermissionMap())).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForAccountAdministrator() {
    setupForAllApp();
    List<UserGroup> userGroups =
        Collections.singletonList(UserGroup.builder()
                                      .accountId(ACCOUNT_ID)
                                      .accountPermissions(accountPermissions)
                                      .appPermissions(new HashSet<>(Collections.singletonList(allAppPermission)))
                                      .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary).isNotNull();
    assertThat(accountPermissionSummary.getPermissions())
        .isNotNull()
        .hasSize(4)
        .hasSameElementsAs(accountPermissionTypes);

    verifyAllAppPermissions(userPermissionInfo);
  }

  private void setupForAllApp() {
    setupForAllApp(false);
  }

  private void setupForAllApp(boolean includeEmptyPipeline) {
    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(appIds);

    List<Service> svcResponse = asList(service1, service2);
    when(serviceResourceService.list(any(), any())).thenReturn(svcResponse);

    PageResponse<InfrastructureProvisioner> infrastructureProvisionersResponse =
        aPageResponse().withResponse(asList(infrastructureProvisioner)).build();
    when(infrastructureProvisionerService.list(any(PageRequest.class))).thenReturn(infrastructureProvisionersResponse);

    PageResponse<Environment> envResponse = aPageResponse().withResponse(asList(dev, qa, prod, dr)).build();
    when(environmentService.list(any(PageRequest.class), eq(false), eq(null), anyBoolean())).thenReturn(envResponse);

    List<Workflow> workflowResponse = asList(workflow1, workflow2, workflow3, workflow4);
    when(workflowService.list(any(), any(), any())).thenReturn(workflowResponse);

    List<Pipeline> pipelines = Lists.newArrayList(pipeline1, pipeline2, pipeline3, pipeline4, pipeline5);
    if (includeEmptyPipeline) {
      pipelines.add(pipeline0);
    }
    PageResponse<Pipeline> pipelineResponse = aPageResponse().withResponse(pipelines).build();
    when(pipelineService.listPipelines(any(PageRequest.class))).thenReturn(pipelineResponse);
  }

  private void setupForNoEnvs() {
    setupForOneEnv(null);
  }

  private void setupForOneEnv(Environment env) {
    when(appService.getAppIdsByAccountId(ACCOUNT_ID)).thenReturn(appIds);
    List<Service> svcResponse = asList(service1, service2);
    when(serviceResourceService.list(any(), any())).thenReturn(svcResponse);

    PageResponse<InfrastructureProvisioner> infrastructureProvisionersResponse =
        aPageResponse().withResponse(asList(infrastructureProvisioner)).build();
    when(infrastructureProvisionerService.list(any(PageRequest.class))).thenReturn(infrastructureProvisionersResponse);

    PageResponse<Environment> envResponse;
    if (env == null) {
      envResponse = aPageResponse().withResponse(asList()).build();
    } else {
      envResponse = aPageResponse().withResponse(asList(env)).build();
    }
    when(environmentService.list(any(PageRequest.class), eq(false), eq(null), anyBoolean())).thenReturn(envResponse);

    List<Workflow> workflowResponse = asList(workflow1, workflow2, workflow3, workflow4, buildWorkflow);
    when(workflowService.list(any(), any(), any())).thenReturn(workflowResponse);

    PageResponse<Pipeline> pipelineResponse = aPageResponse()
                                                  .withResponse(asList(pipeline1, pipeline2, pipeline3, pipeline4,
                                                      pipeline5, buildPipeline, approvalPipeline))
                                                  .build();
    when(pipelineService.listPipelines(any(PageRequest.class))).thenReturn(pipelineResponse);
  }

  private void verifyAllAppPermissions(UserPermissionInfo userPermissionInfo) {
    assertThat(userPermissionInfo.getAppPermissionMap())
        .isNotNull()
        .hasSize(4)
        .containsOnlyKeys(APP_ID, "appId1", "appId2", "appId3");
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID)).isNotNull();
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID))
        .extracting("canCreateService", "canCreateEnvironment", "canCreateWorkflow", "canCreatePipeline")
        .contains(true, true, true, true);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions())
        .isNotNull()
        .containsOnlyKeys(service1.getUuid(), service2.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions().get(service1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getProvisionerPermissions())
        .isNotNull()
        .containsOnlyKeys(infrastructureProvisioner.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getProvisionerPermissions().get(
                   infrastructureProvisioner.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions())
        .isNotNull()
        .containsOnlyKeys(dev.getUuid(), qa.getUuid(), prod.getUuid(), dr.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dev.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(qa.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(prod.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dr.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow2.getUuid(), workflow3.getUuid(), workflow4.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow2.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(
            pipeline1.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(), pipeline4.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline2.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow2.getUuid(), workflow3.getUuid(), workflow4.getUuid(),
            pipeline1.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(), pipeline4.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow2.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline2.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForAppAdministrator() {
    setupForAllApp();
    List<UserGroup> userGroups =
        Collections.singletonList(UserGroup.builder()
                                      .accountId(ACCOUNT_ID)
                                      .appPermissions(new HashSet<>(Collections.singletonList(allAppPermission)))
                                      .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary.getPermissions()).isEmpty();
    verifyAllAppPermissions(userPermissionInfo);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForProdSupport() {
    setupForAllApp();
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(PROD));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(PROD, WorkflowFilter.FilterType.TEMPLATES));

    AppPermission envPermission = AppPermission.builder()
                                      .permissionType(ENV)
                                      .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                      .entityFilter(envFilter)
                                      .actions(new HashSet<>(allActions))
                                      .build();

    AppPermission workflowPermission = AppPermission.builder()
                                           .permissionType(WORKFLOW)
                                           .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                           .entityFilter(workflowFilter)
                                           .actions(new HashSet<>(allActions))
                                           .build();

    AppPermission pipelinePermission = AppPermission.builder()
                                           .permissionType(PIPELINE)
                                           .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                           .entityFilter(envFilter)
                                           .actions(new HashSet<>(allActions))
                                           .build();

    AppPermission deploymentPermission = AppPermission.builder()
                                             .permissionType(DEPLOYMENT)
                                             .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                             .entityFilter(envFilter)
                                             .actions(new HashSet<>(allActions))
                                             .build();

    List<UserGroup> userGroups = Collections.singletonList(
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .appPermissions(
                new HashSet<>(asList(envPermission, workflowPermission, pipelinePermission, deploymentPermission)))
            .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary.getPermissions()).isEmpty();

    assertThat(userPermissionInfo.getAppPermissionMap())
        .isNotNull()
        .hasSize(4)
        .containsOnlyKeys(APP_ID, "appId1", "appId2", "appId3");
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID)).isNotNull();
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID))
        .extracting("canCreateService", "canCreateEnvironment", "canCreateWorkflow", "canCreatePipeline")
        .contains(false, false, false, false);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions()).isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions())
        .isNotNull()
        .containsOnlyKeys(prod.getUuid(), dr.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(prod.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dr.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow3.getUuid(), workflow4.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline2.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(
            workflow3.getUuid(), workflow4.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline2.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline3.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForOneEnvOnly() {
    setupForAllApp(true);
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(EnvFilter.FilterType.SELECTED));
    envFilter.setIds(Sets.newHashSet(dev.getUuid()));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(NON_PROD));

    AppPermission envPermission = constructAppPermission(envFilter, ENV);

    AppPermission workflowPermission = constructAppPermission(workflowFilter, WORKFLOW);

    AppPermission pipelinePermission = constructAppPermission(envFilter, PIPELINE);

    AppPermission deploymentPermission = constructAppPermission(envFilter, DEPLOYMENT);

    List<UserGroup> userGroups = Collections.singletonList(
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .appPermissions(
                new HashSet<>(asList(envPermission, workflowPermission, pipelinePermission, deploymentPermission)))
            .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary.getPermissions()).isEmpty();

    assertThat(userPermissionInfo.getAppPermissionMap()).isNotNull().hasSize(1).containsOnlyKeys(APP_ID);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID)).isNotNull();
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID))
        .extracting("canCreateService", "canCreateEnvironment", "canCreateWorkflow", "canCreatePipeline")
        .contains(false, false, false, false);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions()).isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions())
        .isNotNull()
        .containsOnlyKeys(dev.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dev.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow2.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline0.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline0.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow4.getUuid(), pipeline0.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
  }

  private AppPermission constructAppPermission(EnvFilter envFilter, PermissionType env) {
    return AppPermission.builder()
        .permissionType(env)
        .appFilter(AppFilter.builder().filterType(FilterType.SELECTED).ids(Sets.newHashSet(APP_ID)).build())
        .entityFilter(envFilter)
        .actions(new HashSet<>(allActions))
        .build();
  }

  private AppPermission constructAppPermissionWithEntityFilter(
      GenericEntityFilter genericEntityFilter, PermissionType permissionType) {
    return AppPermission.builder()
        .permissionType(permissionType)
        .appFilter(AppFilter.builder().filterType(FilterType.SELECTED).ids(Sets.newHashSet(APP_ID)).build())
        .entityFilter(genericEntityFilter)
        .actions(new HashSet<>(allActions))
        .build();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForMultiplePermissionsInUserGroup() {
    setupForAllApp();
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(EnvFilter.FilterType.SELECTED));
    envFilter.setIds(Sets.newHashSet(dev.getUuid()));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(NON_PROD));

    AppPermission envPermission = constructAppPermission(envFilter, ENV);

    AppPermission workflowPermission = constructAppPermission(workflowFilter, WORKFLOW);

    AppPermission pipelinePermission = constructAppPermission(envFilter, PIPELINE);

    AppPermission deploymentPermission = constructAppPermission(envFilter, DEPLOYMENT);

    WorkflowFilter workflowFilter1 = new WorkflowFilter();
    workflowFilter1.setFilterTypes(Sets.newHashSet(NON_PROD, PROD));

    AppPermission workflowPermission1 = AppPermission.builder()
                                            .permissionType(WORKFLOW)
                                            .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                            .entityFilter(workflowFilter1)
                                            .actions(Sets.newHashSet(Action.READ))
                                            .build();

    List<UserGroup> userGroups =
        Collections.singletonList(UserGroup.builder()
                                      .accountId(ACCOUNT_ID)
                                      .appPermissions(new HashSet<>(asList(envPermission, workflowPermission,
                                          workflowPermission1, pipelinePermission, deploymentPermission)))
                                      .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary.getPermissions()).isEmpty();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID)).isNotNull();
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID))
        .extracting("canCreateService", "canCreateEnvironment", "canCreateWorkflow", "canCreatePipeline")
        .contains(false, false, false, false);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions()).isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions())
        .isNotNull()
        .containsOnlyKeys(dev.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dev.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow2.getUuid(), workflow3.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .containsExactlyInAnyOrder(
            Action.UPDATE, Action.READ, Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow2.getUuid()))
        .isNotNull()
        .containsExactlyInAnyOrder(
            Action.UPDATE, Action.READ, Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow3.getUuid()))
        .isNotNull()
        .containsExactlyInAnyOrder(Action.READ);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .containsExactlyInAnyOrder(
            Action.UPDATE, Action.READ, Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow4.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForNoEnv() {
    setupForNoEnvs();
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(PROD, NON_PROD));
    testNoEnvPermissions(envFilter, workflowFilter);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForBuildAndApprovalPipeline() {
    setupForNoEnvs();
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(PROD));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(PROD));
    testNoEnvPermissions(envFilter, workflowFilter);
  }

  private void testNoEnvPermissions(EnvFilter envFilter, WorkflowFilter workflowFilter) {
    AppPermission envPermission = constructAppPermission(envFilter, ENV);

    AppPermission workflowPermission = constructAppPermission(workflowFilter, WORKFLOW);

    AppPermission pipelinePermission = constructAppPermission(envFilter, PIPELINE);

    AppPermission deploymentPermission = constructAppPermission(envFilter, DEPLOYMENT);

    List<UserGroup> userGroups = Collections.singletonList(
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .appPermissions(
                new HashSet<>(asList(envPermission, workflowPermission, pipelinePermission, deploymentPermission)))
            .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(buildWorkflow.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(approvalPipeline.getUuid(), buildPipeline.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow4.getUuid(), buildWorkflow.getUuid(), approvalPipeline.getUuid(),
            buildPipeline.getUuid(), pipeline5.getUuid());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testPermissionsForPipelinesInMultipleEnvsAndMultiplePermissions() {
    setupForAllApp();
    EnvFilter devFilter = new EnvFilter();
    devFilter.setFilterTypes(Sets.newHashSet(EnvFilter.FilterType.SELECTED));
    devFilter.setIds(Sets.newHashSet(dev.getUuid()));

    EnvFilter prodFilter = new EnvFilter();
    prodFilter.setFilterTypes(Sets.newHashSet(EnvFilter.FilterType.PROD));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(NON_PROD));

    AppPermission devEnvPermission = constructAppPermission(devFilter, ENV);

    AppPermission workflowPermission = constructAppPermission(workflowFilter, WORKFLOW);

    AppPermission devPipelinePermission = constructAppPermission(devFilter, PIPELINE);

    AppPermission prodPipelinePermission = constructAppPermission(prodFilter, PIPELINE);

    AppPermission devDeploymentPermission = constructAppPermission(devFilter, DEPLOYMENT);

    AppPermission prodDeploymentPermission = constructAppPermission(prodFilter, DEPLOYMENT);

    WorkflowFilter workflowFilter1 = new WorkflowFilter();
    workflowFilter1.setFilterTypes(Sets.newHashSet(NON_PROD, PROD));

    AppPermission workflowPermission1 = AppPermission.builder()
                                            .permissionType(WORKFLOW)
                                            .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
                                            .entityFilter(workflowFilter1)
                                            .actions(Sets.newHashSet(Action.READ))
                                            .build();

    UserGroup devUserGroup = UserGroup.builder()
                                 .accountId(ACCOUNT_ID)
                                 .appPermissions(new HashSet<>(asList(devEnvPermission, workflowPermission,
                                     workflowPermission1, devPipelinePermission, devDeploymentPermission)))
                                 .build();
    UserGroup prodUserGroup = UserGroup.builder()
                                  .accountId(ACCOUNT_ID)
                                  .appPermissions(new HashSet<>(asList(devEnvPermission, workflowPermission,
                                      workflowPermission1, prodPipelinePermission, prodDeploymentPermission)))
                                  .build();

    UserGroup devAndProdUserGroup =
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .appPermissions(new HashSet<>(asList(devEnvPermission, workflowPermission, workflowPermission1,
                devPipelinePermission, prodPipelinePermission, devDeploymentPermission, prodDeploymentPermission)))
            .build();

    // Scenario 1
    List<UserGroup> userGroups = asList(devUserGroup, prodUserGroup);

    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline1.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow3.getUuid(), workflow4.getUuid(), pipeline1.getUuid(),
            pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());

    // Scenario 2
    userGroups = Collections.singletonList(prodUserGroup);

    userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(
            workflow3.getUuid(), workflow4.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());

    // Scenario 3
    userGroups = Collections.singletonList(devAndProdUserGroup);

    userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline1.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow3.getUuid(), workflow4.getUuid(), pipeline1.getUuid(),
            pipeline2.getUuid(), pipeline3.getUuid(), pipeline5.getUuid());

    // Scenario 4
    userGroups = Collections.singletonList(devUserGroup);

    userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline5.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow4.getUuid(), pipeline5.getUuid());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetAllEntities_multiplePages() {
    // Scenario page size = 10, total = 11
    int total = 11;
    int pageSize = 10;
    String pageSizeStr = "10";

    PageRequest<Environment> pageRequest1 =
        PageRequestBuilder.aPageRequest().withLimit(pageSizeStr).withOffset("0").build();
    List<Environment> expectedEnv = createEnvs("dev", 0, total);

    PageResponse<Environment> firstPage =
        aPageResponse().withResponse(createEnvs("dev", 0, pageSize)).withTotal(total).build();
    PageResponse<Environment> lastPage =
        aPageResponse().withResponse(createEnvs("dev", pageSize, total)).withTotal(total).build();

    when(environmentService.list(any(PageRequest.class), eq(false), eq(null), anyBoolean()))
        .thenAnswer((Answer<PageResponse<Environment>>) invocation -> {
          Object[] arguments = invocation.getArguments();
          PageRequest pageRequest = (PageRequest) arguments[0];
          if (pageRequest.getOffset().equals("0")) {
            return firstPage;
          } else if (pageRequest.getOffset().equals(pageSizeStr)) {
            return lastPage;
          } else {
            return null;
          }
        });

    List<Environment> allEntities =
        authHandler.getAllEntities(pageRequest1, () -> environmentService.list(pageRequest1, false, null, false));
    assertThat(total).isEqualTo(allEntities.size());
    assertThat(allEntities).containsExactlyInAnyOrder(expectedEnv.toArray(new Environment[0]));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetAllEntities_multiplePages_multiplesOfPageSize() {
    // Scenario page size = 10, total = 20
    int total = 20;
    int pageSize = 10;
    String pageSizeStr = "10";
    PageRequest<Environment> pageRequest1 =
        PageRequestBuilder.aPageRequest().withLimit(pageSizeStr).withOffset("0").build();
    List<Environment> expectedEnv = createEnvs("dev", 0, total);

    PageResponse<Environment> firstPage =
        aPageResponse().withResponse(createEnvs("dev", 0, 10)).withTotal(total).build();
    PageResponse<Environment> lastPage =
        aPageResponse().withResponse(createEnvs("dev", pageSize, total)).withTotal(total).build();

    when(environmentService.list(any(PageRequest.class), eq(false), eq(null), anyBoolean()))
        .thenAnswer((Answer<PageResponse<Environment>>) invocation -> {
          Object[] arguments = invocation.getArguments();
          PageRequest pageRequest = (PageRequest) arguments[0];
          if (pageRequest.getOffset().equals("0")) {
            return firstPage;
          } else if (pageRequest.getOffset().equals(pageSizeStr)) {
            return lastPage;
          } else {
            return null;
          }
        });

    List<Environment> allEntities =
        authHandler.getAllEntities(pageRequest1, () -> environmentService.list(pageRequest1, false, null, false));
    assertThat(total).isEqualTo(allEntities.size());
    assertThat(allEntities).containsExactlyInAnyOrder(expectedEnv.toArray(new Environment[0]));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetAllEntities_onePage() {
    // Scenario page size = 10, total = 8
    int total = 8;
    String pageSizeStr = "10";
    PageRequest<Environment> pageRequest1 =
        PageRequestBuilder.aPageRequest().withLimit(pageSizeStr).withOffset("0").build();
    List<Environment> expectedEnv = createEnvs("dev", 0, total);

    PageResponse<Environment> firstPage =
        aPageResponse().withResponse(createEnvs("dev", 0, total)).withTotal(total).build();
    when(environmentService.list(eq(pageRequest1), eq(false), eq(null), anyBoolean())).thenReturn(firstPage);

    List<Environment> allEntities =
        authHandler.getAllEntities(pageRequest1, () -> environmentService.list(pageRequest1, false, null, false));
    assertThat(total).isEqualTo(allEntities.size());
    assertThat(allEntities).containsExactlyInAnyOrder(expectedEnv.toArray(new Environment[0]));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetAllEntities_noResults() {
    // Scenario page size = 10, total = 0
    int total = 0;
    String pageSizeStr = "10";

    PageRequest<Environment> pageRequest1 =
        PageRequestBuilder.aPageRequest().withLimit(pageSizeStr).withOffset("0").build();

    PageResponse<Environment> firstPage = aPageResponse().withResponse(Lists.newArrayList()).withTotal(total).build();
    when(environmentService.list(eq(pageRequest1), eq(false), eq(null), anyBoolean())).thenReturn(firstPage);

    List<Environment> allEntities =
        authHandler.getAllEntities(pageRequest1, () -> environmentService.list(pageRequest1, false, null, false));
    assertThat(total).isEqualTo(allEntities.size());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testBuildNonProdSupportUserGroup() {
    UserGroup userGroup = authHandler.buildNonProdSupportUserGroup(ACCOUNT_ID);
    assertThat(userGroup).isNotNull();
    assertThat(userGroup.getAccountPermissions()).isNotNull();
    assertThat(userGroup.getAppPermissions()).isNotNull();

    AccountPermissions accountPermissions = userGroup.getAccountPermissions();
    assertThat(accountPermissions.getPermissions()).contains(AUDIT_VIEWER).contains(CE_VIEWER);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testBuildProdSupportUserGroup() {
    UserGroup userGroup = authHandler.buildProdSupportUserGroup(ACCOUNT_ID);
    assertThat(userGroup).isNotNull();
    assertThat(userGroup.getAccountPermissions()).isNotNull();
    assertThat(userGroup.getAppPermissions()).isNotNull();

    AccountPermissions accountPermissions = userGroup.getAccountPermissions();
    assertThat(accountPermissions.getPermissions()).contains(AUDIT_VIEWER).contains(CE_VIEWER);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testCheckIfPipelineHasOnlyGivenEnvs() {
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline0, Sets.newHashSet(qa.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline0, Sets.newHashSet(prod.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline0, Sets.newHashSet(prod.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(
                   pipeline0, Sets.newHashSet(qa.getUuid(), prod.getUuid(), qa.getUuid())))
        .isTrue();

    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline1, Sets.newHashSet(dev.getUuid(), prod.getUuid())))
        .isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline1, Sets.newHashSet(dev.getUuid()))).isFalse();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline1, Sets.newHashSet(prod.getUuid()))).isFalse();

    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline2, Sets.newHashSet(prod.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline2, Sets.newHashSet(dev.getUuid()))).isFalse();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline2, Sets.newHashSet(dev.getUuid(), prod.getUuid())))
        .isTrue();

    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline3, Sets.newHashSet(prod.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline3, Sets.newHashSet(qa.getUuid()))).isFalse();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline3, Sets.newHashSet(qa.getUuid(), prod.getUuid())))
        .isTrue();

    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline4, Sets.newHashSet(qa.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline4, Sets.newHashSet(prod.getUuid()))).isFalse();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline4, Sets.newHashSet(qa.getUuid(), prod.getUuid())))
        .isTrue();

    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline5, Sets.newHashSet(qa.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline5, Sets.newHashSet(prod.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(pipeline5, Sets.newHashSet(prod.getUuid()))).isTrue();
    assertThat(authHandler.checkIfPipelineHasOnlyGivenEnvs(
                   pipeline5, Sets.newHashSet(qa.getUuid(), prod.getUuid(), qa.getUuid())))
        .isTrue();
  }

  private List<Environment> createEnvs(String prefix, int start, int end) {
    List<Environment> envList = Lists.newArrayList();
    for (int i = start; i < end; i++) {
      envList.add(anEnvironment()
                      .uuid(Integer.toString(i))
                      .name(prefix + i)
                      .appId(APP_ID)
                      .environmentType(EnvironmentType.NON_PROD)
                      .build());
    }
    return envList;
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForWorkflowPipelineEntityFilterTypeAll() {
    setupForAllApp(true);
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(EnvFilter.FilterType.SELECTED));
    envFilter.setIds(Sets.newHashSet(dev.getUuid()));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(NON_PROD));

    GenericEntityFilter entityFilter = GenericEntityFilter.builder().filterType("ALL").build();

    AppPermission envPermission = constructAppPermission(envFilter, ENV);

    AppPermission workflowPermission = constructAppPermissionWithEntityFilter(entityFilter, WORKFLOW);

    AppPermission pipelinePermission = constructAppPermissionWithEntityFilter(entityFilter, PIPELINE);

    AppPermission deploymentPermission = constructAppPermission(envFilter, DEPLOYMENT);

    List<UserGroup> userGroups = Collections.singletonList(
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .appPermissions(
                new HashSet<>(asList(envPermission, workflowPermission, pipelinePermission, deploymentPermission)))
            .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary.getPermissions()).isEmpty();

    assertThat(userPermissionInfo.getAppPermissionMap()).isNotNull().hasSize(1).containsOnlyKeys(APP_ID);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID)).isNotNull();
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID))
        .extracting("canCreateService", "canCreateEnvironment", "canCreateWorkflow", "canCreatePipeline")
        .contains(false, false, false, false);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions()).isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions())
        .isNotNull()
        .containsOnlyKeys(dev.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dev.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    // All Workflows should be present when filterType ALL is used for Workflows
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow2.getUuid(), workflow3.getUuid(), workflow4.getUuid());

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline0.getUuid(), pipeline1.getUuid(), pipeline2.getUuid(), pipeline3.getUuid(),
            pipeline4.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline0.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline5.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow4.getUuid(), pipeline0.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldFetchPermissionsForWorkflowPipelineEntityFilterTypeSelected() {
    setupForAllApp(true);
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(EnvFilter.FilterType.SELECTED));
    envFilter.setIds(Sets.newHashSet(dev.getUuid()));

    WorkflowFilter workflowFilter = new WorkflowFilter();
    workflowFilter.setFilterTypes(Sets.newHashSet(NON_PROD));

    GenericEntityFilter workflowEntityFilter =
        GenericEntityFilter.builder().filterType("SELECTED").ids(Collections.singleton(workflow1.getUuid())).build();

    GenericEntityFilter pipelineEntityFilter =
        GenericEntityFilter.builder().filterType("SELECTED").ids(Collections.singleton(pipeline0.getUuid())).build();

    AppPermission envPermission = constructAppPermission(envFilter, ENV);

    AppPermission workflowPermission = constructAppPermissionWithEntityFilter(workflowEntityFilter, WORKFLOW);

    AppPermission pipelinePermission = constructAppPermissionWithEntityFilter(pipelineEntityFilter, PIPELINE);

    AppPermission deploymentPermission = constructAppPermission(envFilter, DEPLOYMENT);

    List<UserGroup> userGroups = Collections.singletonList(
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .appPermissions(
                new HashSet<>(asList(envPermission, workflowPermission, pipelinePermission, deploymentPermission)))
            .build());
    UserPermissionInfo userPermissionInfo = authHandler.evaluateUserPermissionInfo(ACCOUNT_ID, userGroups, null);

    assertThat(userPermissionInfo).isNotNull().hasFieldOrPropertyWithValue("accountId", ACCOUNT_ID);
    AccountPermissionSummary accountPermissionSummary = userPermissionInfo.getAccountPermissionSummary();
    assertThat(accountPermissionSummary.getPermissions()).isEmpty();

    assertThat(userPermissionInfo.getAppPermissionMap()).isNotNull().hasSize(1).containsOnlyKeys(APP_ID);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID)).isNotNull();
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID))
        .extracting("canCreateService", "canCreateEnvironment", "canCreateWorkflow", "canCreatePipeline")
        .contains(false, false, false, false);

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getServicePermissions()).isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions())
        .isNotNull()
        .containsOnlyKeys(dev.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getEnvPermissions().get(dev.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);

    // Only those Workflows should be present which are selected when filterType SELECTED
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getWorkflowPermissions().get(workflow2.getUuid()))
        .isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions())
        .isNotNull()
        .containsOnlyKeys(pipeline0.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline0.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getPipelinePermissions().get(pipeline5.getUuid()))
        .isNull();

    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions())
        .isNotNull()
        .containsOnlyKeys(workflow1.getUuid(), workflow4.getUuid(), pipeline0.getUuid(), pipeline5.getUuid());
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow1.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
    assertThat(userPermissionInfo.getAppPermissionMap().get(APP_ID).getDeploymentPermissions().get(workflow4.getUuid()))
        .isNotNull()
        .contains(Action.UPDATE, Action.READ, Action.DELETE);
  }
}
