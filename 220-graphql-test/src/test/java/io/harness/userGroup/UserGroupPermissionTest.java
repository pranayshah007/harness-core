/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.userGroup;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ALL_APP_ENTITIES;
import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
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
import static software.wings.security.PermissionAttribute.PermissionType.PROVISIONER;
import static software.wings.security.PermissionAttribute.PermissionType.SERVICE;
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;
import static software.wings.security.PermissionAttribute.PermissionType.WORKFLOW;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.GraphQLTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.category.layer.GraphQLTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.generator.ApplicationGenerator;
import io.harness.generator.EnvironmentGenerator;
import io.harness.generator.InfrastructureProvisionerGenerator;
import io.harness.generator.OwnerManager;
import io.harness.generator.Randomizer;
import io.harness.generator.ServiceGenerator;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Service;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.security.AppFilter;
import software.wings.security.EnvFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.WorkflowFilter;
import software.wings.service.intfc.UserGroupService;

import com.google.inject.Inject;
import graphql.ExecutionResult;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

@OwnedBy(PL)
@Slf4j
public class UserGroupPermissionTest extends GraphQLTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Inject private OwnerManager ownerManager;
  @Inject ApplicationGenerator applicationGenerator;
  @Inject ServiceGenerator serviceGenerator;
  @Inject EnvironmentGenerator environmentGenerator;
  private Application application1, application2;
  private Service service1, service2;
  private Environment environment1, environment2;
  @Inject UserGroupService userGroupService;
  private InfrastructureProvisioner infrastructureProvisioner;
  @Inject InfrastructureProvisionerGenerator infrastructureProvisionerGenerator;
  @Inject UserGroupHelper userGroupHelper;
  private String accountId;
  final Randomizer.Seed seed = new Randomizer.Seed(0);
  private String GenericErrorString = "Exception while fetching data (/updateUserGroupPermissions) : Invalid request: ";

  @Before
  public void setup() {
    // Creating applications
    final OwnerManager.Owners owners = ownerManager.create();
    owners.add(EmbeddedUser.builder().uuid(generateUuid()).build());
    application1 = applicationGenerator.ensureApplication(
        seed, owners, anApplication().name("Application - " + generateUuid()).build());
    accountId = application1.getAccountId();
    application2 = applicationGenerator.ensureApplication(
        seed, owners, anApplication().name("Application - " + generateUuid()).build());

    // Creating Services
    service1 = serviceGenerator.ensureService(seed, owners,
        Service.builder().name("Service1").appId(application1.getUuid()).uuid(UUIDGenerator.generateUuid()).build());
    service2 = serviceGenerator.ensureService(seed, owners,
        Service.builder().name("Service2").appId(application1.getUuid()).uuid(UUIDGenerator.generateUuid()).build());

    // Creating Environment
    final Environment.Builder builder = anEnvironment().appId(application1.getUuid());
    environment1 =
        environmentGenerator.ensureEnvironment(seed, owners, builder.uuid(generateUuid()).name("prod").build());
    environment2 =
        environmentGenerator.ensureEnvironment(seed, owners, builder.uuid(generateUuid()).name("qa").build());

    infrastructureProvisioner = infrastructureProvisionerGenerator.ensurePredefined(
        seed, owners, InfrastructureProvisionerGenerator.InfrastructureProvisioners.TERRAFORM_TEST);
  }

  private Set<PermissionType> createAccountPermissions() {
    List<PermissionType> permissionTypeList = Arrays.asList(MANAGE_APPLICATIONS, USER_PERMISSION_READ,
        USER_PERMISSION_MANAGEMENT, TEMPLATE_MANAGEMENT, ACCOUNT_MANAGEMENT, AUDIT_VIEWER, MANAGE_TAGS,
        MANAGE_ACCOUNT_DEFAULTS, CE_ADMIN, CE_VIEWER, MANAGE_CLOUD_PROVIDERS, MANAGE_CONNECTORS,
        MANAGE_APPLICATION_STACKS, MANAGE_DELEGATES, MANAGE_ALERT_NOTIFICATION_RULES, MANAGE_DELEGATE_PROFILES,
        MANAGE_CONFIG_AS_CODE, MANAGE_SECRETS, MANAGE_SECRET_MANAGERS, MANAGE_AUTHENTICATION_SETTINGS,
        MANAGE_IP_WHITELIST, MANAGE_DEPLOYMENT_FREEZES, MANAGE_PIPELINE_GOVERNANCE_STANDARDS, MANAGE_API_KEYS,
        MANAGE_SSH_AND_WINRM, MANAGE_RESTRICTED_ACCESS, HIDE_NEXTGEN_BUTTON);
    return new HashSet<>(permissionTypeList);
  }

  private String createAccountPermissionGQL(String userGroupId) {
    String accountPermissions =
        $GQL(/*
{
userGroupId: "%s",
clientMutationId: "abc",
permissions: {
accountPermissions : {
accountPermissionTypes: [
CREATE_AND_DELETE_APPLICATION,
READ_USERS_AND_GROUPS,
MANAGE_USERS_AND_GROUPS,
MANAGE_TEMPLATE_LIBRARY,
ADMINISTER_OTHER_ACCOUNT_FUNCTIONS,
VIEW_AUDITS,
MANAGE_TAGS, MANAGE_ACCOUNT_DEFAULTS, ADMINISTER_CE,
  VIEW_CE, MANAGE_CLOUD_PROVIDERS, MANAGE_CONNECTORS, MANAGE_APPLICATION_STACKS, MANAGE_DELEGATES,
        MANAGE_ALERT_NOTIFICATION_RULES, MANAGE_DELEGATE_PROFILES, MANAGE_CONFIG_AS_CODE, MANAGE_SECRETS,
        MANAGE_SECRET_MANAGERS, MANAGE_SSH_AND_WINRM, MANAGE_AUTHENTICATION_SETTINGS, MANAGE_API_KEYS
        , MANAGE_IP_WHITELIST, MANAGE_DEPLOYMENT_FREEZES,
        MANAGE_PIPELINE_GOVERNANCE_STANDARDS, MANAGE_RESTRICTED_ACCESS, HIDE_NEXTGEN_BUTTON
]
}
}
}*/ userGroupId);
    return accountPermissions;
  }

  private Set<Action> createActionSet() {
    List<Action> actionsList = Arrays.asList(Action.CREATE, Action.READ, Action.DELETE);
    return new HashSet<>(actionsList);
  }

  private String createMutation(String userGroupId, String permissionsVariable) {
    String mutation =
        $GQL(/*
mutation {
    updateUserGroupPermissions(input: %s){
      permissions{
        accountPermissions{
          accountPermissionTypes
         }
       }
    }
}*/ permissionsVariable);
    return mutation;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testAccountPermissions() {
    // Create Permissions
    Set<PermissionType> allPermissions = createAccountPermissions();
    AccountPermissions accountPermissions = AccountPermissions.builder().permissions(allPermissions).build();
    // Add Permission to userGroup
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, accountPermissions, null);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using graphql
    String accountPermissionsString = createAccountPermissionGQL(userGroupId);
    String mutationAccountPermissions = createMutation(userGroupId, accountPermissionsString);

    {
      // This query will update the userGroup
      qlExecute(mutationAccountPermissions, accountId);
    }
    // Compare this two
    UserGroup updatedUserGroup = userGroupService.get(accountId, userGroupId);
    assertThat(updatedUserGroup.getAccountPermissions().getPermissions())
        .containsExactlyInAnyOrderElementsOf(userGroup.getAccountPermissions().getPermissions());
    // delete the userGroup
    userGroupService.delete(accountId, userGroupId, true);
  }

  private void testAppPermissions(UserGroup userGroup, String appPermissionString) {
    String userGroupId = userGroup.getUuid();
    String mutationAccountPermissions = createMutation(userGroupId, appPermissionString);

    {
      // This query will update the userGroup
      qlExecute(mutationAccountPermissions, accountId);
    }
    // Compare this two
    UserGroup updatedUserGroup = userGroupService.get(accountId, userGroupId);
    assertThat(updatedUserGroup.getAppPermissions().size()).isEqualTo(userGroup.getAppPermissions().size());
    // delete the userGroup
    userGroupService.delete(accountId, userGroupId, true);
  }

  private String createAllEntityAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
       appPermissions :
                             [
                          {
                             permissionType : ALL,
                              applications:    {
                                                  filterType: ALL
                                                                                           },
                              actions:        [
                                                CREATE,DELETE,READ]
                                                 }
                                  ]
        }
    }*/ userGroupId);

    return appPermissionString;
  }

  private GenericEntityFilter createGenericFilterwithTypeAll() {
    return GenericEntityFilter.builder().filterType("ALL").build();
  }
  private GenericEntityFilter createGenericFilterwithIds(String id1, String id2) {
    Set<String> ids = new HashSet<>(Arrays.asList(id1, id2));
    return GenericEntityFilter.builder().filterType("SELECTED").ids(ids).build();
  }

  private AppFilter createAppFilterwithTypeAll() {
    return AppFilter.builder().filterType(AppFilter.FilterType.ALL).build();
  }
  private AppFilter createAppFilterwithIds(String id1, String id2) {
    Set<String> ids = new HashSet<>(Arrays.asList(id1, id2));
    return AppFilter.builder().filterType(AppFilter.FilterType.SELECTED).ids(ids).build();
  }

  private AppPermission createAllEntityAllAppsPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithTypeAll();
    return AppPermission.builder().permissionType(ALL_APP_ENTITIES).actions(actions).appFilter(appFilter).build();
  }

  private AppPermission createAllEntitySelectedAppsPermission() {
    AppFilter appFilter = createAppFilterwithIds(application1.getUuid(), application2.getUuid());
    return AppPermission.builder()
        .permissionType(ALL_APP_ENTITIES)
        .actions(createActionSet())
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testAllEntityAppPermissions() {
    AppPermission allApplications = createAllEntityAllAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(allApplications);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createAllEntityAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String getAppIdsFilter() {
    return String.format("appIds: [\"%s\", \"%s\"]", application1.getUuid(), application2.getUuid());
  }

  private String createAllEntitySelectedAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
            appPermissions :
                            [
                              {
                              permissionType : ALL,
                              applications:    {
                                                  %s
                                               },
                              actions:        [CREATE,DELETE,READ]
                              }
                            ]
       }
    }*/ userGroupId, getAppIdsFilter());

    return appPermissionString;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testPermissionsforSelectedApplications() {
    AppPermission selectedApplications = createAllEntitySelectedAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(selectedApplications);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createAllEntitySelectedAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String createServiceAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                          [
                          {
                             permissionType : SERVICE,
                             applications   : {
                                                 filterType: ALL
                                              },
                              services      :  {
                                                     filterType :ALL
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  private AppPermission createServiceAllAppPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithTypeAll();
    GenericEntityFilter serviceFilter = createGenericFilterwithTypeAll();
    return AppPermission.builder()
        .permissionType(SERVICE)
        .entityFilter(serviceFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testServiceAppPermissions() {
    AppPermission appPermission = createServiceAllAppPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createServiceAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String getServiceIds() {
    return String.format("serviceIds: [\"%s\", \"%s\"]", service1.getUuid(), service2.getUuid());
  }

  private String createServiceWithIdPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
           appPermissions :
                             [
                          {
                             permissionType : SERVICE,
                             applications   : {
                                                 %s
                                               },
                             services      :  {
                                                     %s
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId, getAppIdsFilter(), getServiceIds());

    return appPermissionString;
  }

  private AppPermission createServiceWithIdPermission() {
    AppFilter appFilter = createAppFilterwithIds(application1.getUuid(), application2.getUuid());
    GenericEntityFilter serviceFilter = createGenericFilterwithIds(service1.getUuid(), service2.getUuid());
    return AppPermission.builder()
        .permissionType(SERVICE)
        .entityFilter(serviceFilter)
        .actions(createActionSet())
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testServiceAppPermissionsWithIds() {
    AppPermission appPermission = createServiceWithIdPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createServiceWithIdPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String createEnvAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
       appPermissions :
                             [
                          {
                             permissionType : ENV,
                             applications   : {
                                                 filterType: ALL
                                              },
                             environments      :  {
                                                     filterTypes :
[PRODUCTION_ENVIRONMENTS,NON_PRODUCTION_ENVIRONMENTS]
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
      }
}*/ userGroupId);

    return appPermissionString;
  }

  private AppPermission createEnvAllAppsPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithTypeAll();
    List<String> envList = Arrays.asList("PROD", "NON_PROD");
    Set<String> envFilterTypes = new HashSet<>(envList);
    EnvFilter envFilter = EnvFilter.builder().filterTypes(envFilterTypes).build();
    return AppPermission.builder()
        .permissionType(ENV)
        .entityFilter(envFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testEnvAppPermissions() {
    AppPermission appPermission = createEnvAllAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createEnvAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String getEnvFilter() {
    return String.format("envIds: [\"%s\", \"%s\"]", environment1.getUuid(), environment2.getUuid());
  }

  private String createEnvWithIdsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                             [
                          {
                             permissionType : ENV,
                             applications   : {
                                                 %s
                                              },
                             environments      :  {
                                                     %s
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
        }
}*/ userGroupId, getAppIdsFilter(), getEnvFilter());

    return appPermissionString;
  }

  private AppPermission createEnvWithIdsPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithIds(application1.getUuid(), application2.getUuid());
    List<String> envList = Arrays.asList(environment1.getUuid(), environment2.getUuid());
    Set<String> ids = new HashSet<>(envList);
    EnvFilter envFilter = EnvFilter.builder().ids(ids).filterTypes(new HashSet<>(Arrays.asList("SELECTED"))).build();
    return AppPermission.builder()
        .permissionType(ENV)
        .entityFilter(envFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testEnvWithIdsPermissions() {
    AppPermission appPermission = createEnvWithIdsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createEnvWithIdsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String createWorkflowAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
       appPermissions :
                             [
                          {
                             permissionType : WORKFLOW ,
                             applications   : {
                                                 filterType: ALL
                                              },
                             workflows      :  {
                                                 filterTypes :
[PRODUCTION_WORKFLOWS,NON_PRODUCTION_WORKFLOWS,WORKFLOW_TEMPLATES]
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  private AppPermission createWorkflowAllAppsPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithTypeAll();
    List<String> workflowList = Arrays.asList("PROD", "NON_PROD", "TEMPLATES");
    Set<String> workflowFilterTypes = new HashSet<>(workflowList);
    WorkflowFilter workflowFilter = new WorkflowFilter(null, workflowFilterTypes);
    return AppPermission.builder()
        .permissionType(WORKFLOW)
        .entityFilter(workflowFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testWorkflowAppPermissions() {
    AppPermission appPermission = createWorkflowAllAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createWorkflowAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String createDeploymentAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                             [
                          {
                             permissionType : DEPLOYMENT ,
                             applications   : {
                                                 filterType: ALL
                                              },
                             deployments      :  {
                                                     filterTypes :
[PRODUCTION_ENVIRONMENTS,NON_PRODUCTION_ENVIRONMENTS]
                                               },
                              actions:        [READ]
                                  }
                                 ]
        }
}*/ userGroupId);

    return appPermissionString;
  }

  private AppPermission createDeploymentAllAppsPermission() {
    Set<Action> actions = new HashSet<>(Arrays.asList(Action.READ));
    AppFilter appFilter = createAppFilterwithTypeAll();
    List<String> deploymentList = Arrays.asList("PROD", "NON_PROD");
    Set<String> deploymentFilterTypes = new HashSet<>(deploymentList);
    EnvFilter deploymentFilter = EnvFilter.builder().filterTypes(deploymentFilterTypes).build();
    return AppPermission.builder()
        .permissionType(DEPLOYMENT)
        .entityFilter(deploymentFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testDeploymentAppPermissions() {
    AppPermission appPermission = createDeploymentAllAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createDeploymentAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String createPiplelineAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
       appPermissions :
                             [
                          {
                             permissionType : PIPELINE ,
                             applications   : {
                                                 filterType: ALL
                                              },
                             pipelines      :  {
                                                     filterTypes :
[PRODUCTION_PIPELINES,NON_PRODUCTION_PIPELINES]
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  private AppPermission createPipelineAllAppsPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithTypeAll();
    List<String> deploymentList = Arrays.asList("PROD", "NON_PROD");
    Set<String> deploymentFilterTypes = new HashSet<>(deploymentList);
    EnvFilter deploymentFilter = EnvFilter.builder().filterTypes(deploymentFilterTypes).build();
    return AppPermission.builder()
        .permissionType(PIPELINE)
        .entityFilter(deploymentFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }
  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testPipelineAppPermissions() {
    AppPermission appPermission = createPipelineAllAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createPiplelineAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String createProvisionerseAllAppsPermissionGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
       appPermissions :
                             [
                          {
                             permissionType :PROVISIONER,
                             applications   : {
                                                 filterType: ALL
                                              },
                             provisioners      :  {
                                                     filterType : ALL
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  private AppPermission createProvisonersAllAppsPermission() {
    Set<Action> actions = createActionSet();
    AppFilter appFilter = createAppFilterwithTypeAll();
    GenericEntityFilter provisionerFilter = createGenericFilterwithTypeAll();
    return AppPermission.builder()
        .permissionType(PROVISIONER)
        .entityFilter(provisionerFilter)
        .actions(actions)
        .appFilter(appFilter)
        .build();
  }
  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testProvisonersPermissions() {
    AppPermission appPermission = createProvisonersAllAppsPermission();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createProvisionerseAllAppsPermissionGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private String getprovisionerFilter() {
    return String.format("provisionerIds: [\"%s\"]", infrastructureProvisioner.getUuid());
  }

  private String createListOfAppPermissionsGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
       appPermissions :
                          [
                            {
                             permissionType :PROVISIONER,
                             applications   :  {
                                                   %s
                                               },
                             provisioners    : {
                                                     %s
                                               },
                              actions:        [CREATE,DELETE,READ]
                             },
                             {
                             permissionType :DEPLOYMENT,
                             applications   :  {
                                                 %s
                                               },
                             deployments     : {
                                                     %s
                                               },
                              actions:        [EXECUTE_WORKFLOW, EXECUTE_PIPELINE,READ]
                             },
                             {
                             permissionType :PIPELINE,
                             applications   :  {
                                                 %s
                                               },
                             pipelines    :    {
                                                     %s
                                               },
                              actions:        [CREATE,DELETE,READ]
                             }
                           ]
     }
}*/ userGroupId, getAppIdsFilter(), getprovisionerFilter(), getAppIdsFilter(), getEnvFilter(), getAppIdsFilter(),
        getEnvFilter());
    return appPermissionString;
  }

  private GenericEntityFilter createGenericFilterwithId(String id) {
    Set<String> ids = new HashSet<>(Arrays.asList(id));
    return GenericEntityFilter.builder().filterType("SELECTED").ids(ids).build();
  }
  private Set<AppPermission> createListOfAppPermissions() {
    Set<AppPermission> appPermissions = new HashSet<>();
    AppFilter appFilter = createAppFilterwithIds(application1.getUuid(), application2.getUuid());
    GenericEntityFilter provisionerFilter = createGenericFilterwithId(infrastructureProvisioner.getUuid());
    AppPermission provionserPermissions = AppPermission.builder()
                                              .permissionType(PROVISIONER)
                                              .entityFilter(provisionerFilter)
                                              .actions(createActionSet())
                                              .appFilter(appFilter)
                                              .build();
    List<String> envList = Arrays.asList(environment1.getUuid(), environment2.getUuid());
    Set<String> ids = new HashSet<>(envList);
    EnvFilter envFilter = EnvFilter.builder().ids(ids).filterTypes(new HashSet<>(Arrays.asList("SELECTED"))).build();
    Set<Action> deploymentActions =
        new HashSet<>(Arrays.asList(Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW, Action.READ));

    AppPermission deploymentPermissions = AppPermission.builder()
                                              .permissionType(DEPLOYMENT)
                                              .entityFilter(envFilter)
                                              .actions(deploymentActions)
                                              .appFilter(appFilter)
                                              .build();
    AppPermission pipelinePermission = AppPermission.builder()
                                           .permissionType(PIPELINE)
                                           .entityFilter(envFilter)
                                           .actions(createActionSet())
                                           .appFilter(appFilter)
                                           .build();
    appPermissions.add(provionserPermissions);
    appPermissions.add(deploymentPermissions);
    appPermissions.add(pipelinePermission);
    return appPermissions;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testUpdatingMultipleAppPermissions() {
    Set<AppPermission> appPermissions = createListOfAppPermissions();
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, null, appPermissions);
    String userGroupId = userGroup.getUuid();
    // Add Permission to userGroup using gql
    String appPermissionString = createListOfAppPermissionsGQL(userGroupId);
    testAppPermissions(userGroup, appPermissionString);
  }

  private ExecutionResult doGraphQLRequest(UserGroup userGroup, String appPermissionString) {
    String userGroupId = userGroup.getUuid();
    String mutationAccountPermissions = createMutation(userGroupId, appPermissionString);
    ExecutionResult result;
    // This query will update the userGroup
    result = qlResult(mutationAccountPermissions, accountId);
    return result;
  }

  private String createAppPermissionWithoutPermissionType(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
         appPermissions :
                             [
                          {
                             applications   : {
                                                 filterType: ALL
                                              },
                             deployments      :  {
                                                     filterTypes :
[PRODUCTION_ENVIRONMENTS,NON_PRODUCTION_ENVIRONMENTS]
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  private UserGroup createUserGroup() {
    Set<PermissionType> allPermissions = createAccountPermissions();
    AccountPermissions accountPermissions = AccountPermissions.builder().permissions(allPermissions).build();
    UserGroup userGroup = userGroupHelper.createUserGroupWithPermissions(accountId, accountPermissions, null);
    return userGroup;
  }

  /*Adding set of negative test cases
   */
  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testWhenPermissionTypeNotGiven() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createAppPermissionWithoutPermissionType(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);
  }

  private String createAppPermissionWithoutApplications(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                             [
                          {
                             permissionType :PROVISIONER,
                             provisioners      :  {
                                                     filterType : ALL
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  /*Adding set of negative test cases
   */
  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testWhenApplicationsNotGiven() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createAppPermissionWithoutApplications(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);
  }

  private String createAppPermissionWithIncorrectFilter(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
         appPermissions :
                             [
                          {
                             permissionType : ENV ,
                             applications   : {
                                                 filterType: ALL
                                              },
                             workflows      :  {
                                                 filterTypes :
[PRODUCTION_WORKFLOWS,NON_PRODUCTION_WORKFLOWS,WORKFLOW_TEMPLATES]
                                               },
                              actions:        [CREATE,DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testWhenCorrectFilterNotGiven() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createAppPermissionWithIncorrectFilter(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo(GenericErrorString + "No ENV filter provided with the permission with type ENV");
  }

  private String createDeploymentWithWrongActionsGQL(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
         appPermissions :
                             [
                          {
                             permissionType : DEPLOYMENT ,
                             applications   : {
                                                 filterType: ALL
                                              },
                             deployments      :  {
                                                     filterTypes :
[PRODUCTION_ENVIRONMENTS,NON_PRODUCTION_ENVIRONMENTS]
                                               },
                              actions:        [DELETE,READ]
                                  }
                                 ]
       }
}*/ userGroupId);
    return appPermissionString;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testInvalidActionInDeployment() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createDeploymentWithWrongActionsGQL(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo(GenericErrorString + "Invalid action DELETE for the DEPLOYMENT permission type");
  }

  private String createAllAppWithProvisionerIdPermission(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                       [
                          {
                             permissionType :PROVISIONER,
                             applications   : {
                                                 filterType: ALL
                                              },
                             provisioners      :  {
                                                     provisionerIds : ["abc"]
                                               },
                              actions:        [CREATE,DELETE,READ]
                           }
                        ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testNoIdsForAllAppsPermission() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createAllAppWithProvisionerIdPermission(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo(GenericErrorString
            + "PROVISIONER Ids should not be supplied with AppFilter=\"ALL Applications\" for filterType PROVISIONER");
  }

  private String createPermissionWithInvalidAppId(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                        [
                          {
                             permissionType : ENV,
                             applications   : {
                                                 appIds: ["abc"]
                                              },
                             environments      :  {
                                                     filterTypes :
[PRODUCTION_ENVIRONMENTS,NON_PRODUCTION_ENVIRONMENTS]
                                               },
                              actions:        [CREATE,DELETE,READ]
                           }
                         ]
       }
}*/ userGroupId);

    return appPermissionString;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testWhetherAppIdIsCorrect() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createPermissionWithInvalidAppId(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo(GenericErrorString + "Invalid app id/s abc provided in the request");
  }

  private String createPermissionWithInvalidEnvId(String userGroupId) {
    String appPermissionString = $GQL(
        /*
{
       userGroupId: "%s",
       clientMutationId: "abc",
       permissions: {
          appPermissions :
                        [
                          {
                             permissionType : PIPELINE ,
                             applications   : {
                                                 %s
                                              },
                             pipelines      :  {
                                                     envIds :["abc"]
                                               },
                             actions:        [CREATE,DELETE,READ]
                           }
                         ]
      }
}*/ userGroupId, getAppIdsFilter());

    return appPermissionString;
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category({GraphQLTests.class, UnitTests.class})
  public void testWhetherEnvIdIsCorrect() {
    UserGroup userGroup = createUserGroup();
    String appPermissionString = createPermissionWithInvalidEnvId(userGroup.getUuid());
    ExecutionResult result = doGraphQLRequest(userGroup, appPermissionString);
    assertThat(result.getErrors().size()).isEqualTo(1);

    assertThat(result.getErrors().get(0).getMessage())
        .isEqualTo(GenericErrorString + "Invalid env id/s abc provided in the request");
  }
}
