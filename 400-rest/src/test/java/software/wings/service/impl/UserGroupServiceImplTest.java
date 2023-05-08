/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.NIKOLA;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.REETIKA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VOJIN;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.security.UserGroup.DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.DEFAULT_READ_ONLY_USER_GROUP_NAME;
import static software.wings.beans.security.UserGroup.builder;
import static software.wings.security.AppFilter.FilterType.ALL;
import static software.wings.security.EnvFilter.FilterType.PROD;
import static software.wings.security.EnvFilter.FilterType.SELECTED;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.ALL_APP_ENTITIES;
import static software.wings.security.PermissionAttribute.PermissionType.APP_TEMPLATE;
import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.ENV;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ACCOUNT_DEFAULTS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_APPLICATIONS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_TAGS;
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;
import static software.wings.security.UserThreadLocal.userGuard;
import static software.wings.service.impl.UserServiceImpl.ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME;
import static software.wings.service.impl.UserServiceImpl.INVITE_EMAIL_TEMPLATE_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT1_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.UUID1;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.category.element.UnitTests;
import io.harness.ccm.config.CCMSettingService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UserGroupAlreadyExistException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.LimitCheckerFactory;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.Account.Builder;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Role;
import software.wings.beans.User;
import software.wings.beans.UserGroupEntityReference;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.AccountPermissions;
import software.wings.beans.security.AppPermission;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.security.UserGroupSearchTermType;
import software.wings.features.api.UsageLimitedFeature;
import software.wings.persistence.mail.EmailData;
import software.wings.security.AppFilter;
import software.wings.security.EnvFilter;
import software.wings.security.Filter;
import software.wings.security.GenericEntityFilter.FilterType;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.EmailNotificationService;
import software.wings.service.intfc.RoleService;
import software.wings.service.intfc.UserService;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
@OwnedBy(PL)
@TargetModule(_360_CG_MANAGER)
public class UserGroupServiceImplTest extends WingsBaseTest {
  private String accountId = generateUuid();
  private String userGroupId = generateUuid();
  private String userGroup2Id = generateUuid();
  private String userGroup3Id = generateUuid();
  private String description = "test description";
  private String userGroupName = "userGroup1";
  private String userGroupName2 = "userGroup2";
  private String userGroupName3 = "userGroup3";
  private String userName1 = "UserName1";
  private String userName2 = "auserName2";
  private String userName = "UserName";
  private String user1Id = generateUuid();
  private String user2Id = generateUuid();
  private String userId = generateUuid();
  private AppPermission envPermission = getEnvPermission();
  private List<String> appIds = asList("appId1", "appId2");
  private Set<Action> actions = new HashSet<>(Arrays.asList(Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE));

  private Account account = anAccount()
                                .withAccountName(ACCOUNT_NAME)
                                .withCompanyName(COMPANY_NAME)
                                .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                                .withUuid(ACCOUNT_ID)
                                .build();

  private User user = anUser()
                          .uuid(generateUuid())
                          .appId(APP_ID)
                          .email(USER_EMAIL)
                          .name(USER_NAME)
                          .password(PASSWORD)
                          .accountName(ACCOUNT_NAME)
                          .companyName(COMPANY_NAME)
                          .build();

  private UserGroup userGroup1 =
      builder()
          .accountId(accountId)
          .uuid(userGroupId)
          .name(userGroupName + System.currentTimeMillis())
          .description(description)
          .memberIds(asList(user1Id, user2Id))
          .appPermissions(Sets.newHashSet(envPermission))
          .accountPermissions(AccountPermissions.builder()
                                  .permissions(new HashSet<>(Collections.singleton(ACCOUNT_MANAGEMENT)))
                                  .build())
          .isDefault(true)
          .build(); // defaultUserGroup

  private UserGroup userGroup2 =
      builder()
          .accountId(accountId)
          .uuid(userGroupId)
          .name(userGroupName)
          .description(description)
          .memberIds(singletonList(user1Id))
          .accountPermissions(
              AccountPermissions.builder().permissions(new HashSet<>(Collections.singleton(AUDIT_VIEWER))).build())
          .isDefault(false)
          .build(); // nonDefaultUserGroup

  private UserGroup userGroup3 = builder()
                                     .accountId(accountId)
                                     .name(userGroupName2)
                                     .memberIds(singletonList(user.getUuid()))
                                     .isDefault(true)
                                     .build();

  @Mock private AuthService authService;
  @Mock private RoleService roleService;
  @Mock private AccountService accountService;
  @Mock private EmailNotificationService emailNotificationService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private CCMSettingService ccmSettingService;
  @Mock private AppService appService;
  @Mock private FeatureFlagService featureFlagService;

  @Inject private HPersistence persistence;
  //  @InjectMocks @Inject private AccountService accountService = spy(AccountServiceImpl.class);
  @InjectMocks @Inject private UserService userService;
  @InjectMocks @Inject private AuthHandler authHandler;
  @InjectMocks @Inject private UserGroupServiceImpl userGroupService;
  @Mock private UsageLimitedFeature rbacFeature;

  private static final String MICROSOFT_TEAMS_WEBHOOK_URL = "https://microsoftTeamsWebhookUrl";

  @Before
  public void setup() {
    doNothing().when(authService).evictUserPermissionAndRestrictionCacheForAccount(anyString(), anyList());
    when(accountService.get(anyString())).thenReturn(account);
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    when(roleService.getAccountAdminRole(any()))
        .thenReturn(Role.Builder.aRole().withAccountId(ACCOUNT_ID).withUuid(generateUuid()).build());
    when(rbacFeature.getMaxUsageAllowedForAccount(accountId)).thenReturn(Integer.MAX_VALUE);
    when(rbacFeature.getMaxUsageAllowedForAccount(ACCOUNT_ID)).thenReturn(Integer.MAX_VALUE);
    when(ccmSettingService.isCloudCostEnabled(eq(accountId))).thenReturn(false);
    persistence.save(user);
  }

  private Account createAndSaveAccount(String id) {
    Account account = new Account();
    account.setUuid(id);
    account.setLicenseInfo(LicenseInfo.builder().accountType("PAID").build());
    String accountId = persistence.save(account);
    return persistence.get(Account.class, accountId);
  }

  private User createUserAndSave(
      @NotNull String userId, @NotNull String userName, @NotNull UserGroup userGroup, @NotNull Account testAccount) {
    User user = new User();
    user.setDefaultAccountId(testAccount.getUuid());
    user.setUuid(userId);
    user.setName(userName);
    user.setEmail("user@harness.io");
    user.setAccounts(Arrays.asList(testAccount));
    user.setUserGroups(Arrays.asList(userGroup));
    userService.createUser(user, testAccount.getUuid());
    return userService.get(userId);
  }

  private void compareUsersOrder(UserGroup userGroup, String firstUserName, String secondUserName) {
    assertThat(userGroup.getMembers().get(0).getName()).isEqualTo(firstUserName);
    assertThat(userGroup.getMembers().get(1).getName()).isEqualTo(secondUserName);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testLoadUsersForUserSortOrder() {
    account = createAndSaveAccount(accountId);
    when(accountService.get(accountId)).thenReturn(account);
    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name(userGroupName + System.currentTimeMillis())
                              .appPermissions(Sets.newHashSet(envPermission))
                              .memberIds(asList(user1Id, user2Id))
                              .build();

    User user1 = createUserAndSave(user1Id, userName1, userGroup, account);
    User user2 = createUserAndSave(user2Id, userName2, userGroup, account);

    UserGroup savedUserGroup = userGroupService.save(userGroup);
    compareUsersOrder(savedUserGroup, userName2, userName1);

    userGroup = builder()
                    .accountId(accountId)
                    .uuid(userGroup2Id)
                    .description(description)
                    .name(userGroupName + System.currentTimeMillis())
                    .appPermissions(Sets.newHashSet(envPermission))
                    .memberIds(asList(user2Id, user1Id, userId))
                    .build();

    user1.getUserGroups().add(userGroup);
    userService.createUser(user1, accountId);
    user2.getUserGroups().add(userGroup);
    userService.createUser(user2, accountId);

    createUserAndSave(userId, userName, userGroup, account);

    savedUserGroup = userGroupService.save(userGroup);
    compareUsersOrder(savedUserGroup, userName2, userName);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSaveAndGetForAdminWhenCeDisabled() {
    UserGroup savedUserGroup = userGroupService.save(userGroup1);
    assertThat(savedUserGroup)
        .isEqualToComparingOnlyGivenFields(userGroup1, "uuid", UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions);

    UserGroup userGroupFromGet = userGroupService.get(accountId, userGroupId);
    assertThat(userGroupFromGet)
        .isEqualToComparingOnlyGivenFields(savedUserGroup, "uuid", UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSaveAndGetForNonAdminWhenCeDisabled() {
    UserGroup savedUserGroup = userGroupService.save(userGroup2);
    assertThat(savedUserGroup)
        .isEqualToComparingOnlyGivenFields(userGroup2, "uuid", UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions);

    UserGroup userGroupFromGet = userGroupService.get(accountId, userGroupId);
    assertThat(userGroupFromGet)
        .isEqualToComparingOnlyGivenFields(savedUserGroup, "uuid", UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSaveAndGetForAdminWithCeEnabled() {
    when(ccmSettingService.isCloudCostEnabled(eq(accountId))).thenReturn(true);

    UserGroup savedUserGroup = userGroupService.save(userGroup1);
    assertThat(savedUserGroup)
        .isEqualToComparingOnlyGivenFields(userGroup1, UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions, "uuid");
    assertThat(savedUserGroup.getAccountPermissions().getPermissions())
        .contains(CE_ADMIN, CE_VIEWER, ACCOUNT_MANAGEMENT);

    UserGroup userGroupFromGet = userGroupService.get(accountId, userGroupId);
    assertThat(userGroupFromGet)
        .isEqualToComparingOnlyGivenFields(savedUserGroup, "uuid", UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions);
    assertThat(savedUserGroup.getAccountPermissions().getPermissions())
        .contains(CE_ADMIN, CE_VIEWER, ACCOUNT_MANAGEMENT);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testSaveAndGetForNonAdminWithCeEnabled() {
    when(ccmSettingService.isCloudCostEnabled(eq(accountId))).thenReturn(true);
    UserGroup savedUserGroup = userGroupService.save(userGroup2);
    assertThat(savedUserGroup)
        .isEqualToComparingOnlyGivenFields(userGroup2, UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions, "uuid");
    assertThat(savedUserGroup.getAccountPermissions().getPermissions()).contains(CE_VIEWER);

    UserGroup userGroupFromGet = userGroupService.get(accountId, userGroupId);
    assertThat(userGroupFromGet)
        .isEqualToComparingOnlyGivenFields(savedUserGroup, UserGroupKeys.accountId, UserGroupKeys.name,
            UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions, "uuid");
    assertThat(userGroupFromGet.getAccountPermissions().getPermissions()).contains(CE_VIEWER);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testSaveAndGet() {
    UserGroup savedUserGroup = userGroupService.save(userGroup1);
    compare(userGroup1, savedUserGroup);

    UserGroup userGroupFromGet = userGroupService.get(accountId, userGroupId);
    compare(savedUserGroup, userGroupFromGet);

    savedUserGroup = userGroupService.save(userGroup2);
    compare(userGroup2, savedUserGroup);

    userGroupFromGet = userGroupService.get(accountId, userGroupId);
    compare(savedUserGroup, userGroupFromGet);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldListByAccountIdWithCeEnabled() {
    userGroup2.setMemberIds(singletonList(user.getUuid()));
    UserGroup savedUserGroup2 = userGroupService.save(userGroup2);
    UserGroup savedUserGroup3 = userGroupService.save(userGroup3);

    assertThat(userGroupService.listByAccountId(accountId, user, true))
        .containsAll(asList(savedUserGroup2, savedUserGroup3));
  }

  private AppPermission getEnvPermission() {
    List<Action> allActions = asList(
        Action.CREATE, Action.UPDATE, Action.READ, Action.DELETE, Action.EXECUTE_PIPELINE, Action.EXECUTE_WORKFLOW);
    EnvFilter envFilter = new EnvFilter();
    envFilter.setFilterTypes(Sets.newHashSet(PROD));

    return AppPermission.builder()
        .permissionType(ENV)
        .appFilter(AppFilter.builder().filterType(FilterType.ALL).build())
        .entityFilter(envFilter)
        .actions(new HashSet<>(allActions))
        .build();
  }

  private void compare(UserGroup expectedhs, UserGroup actualhs) {
    assertThat(actualhs).isEqualToComparingOnlyGivenFields(expectedhs, "uuid", UserGroupKeys.accountId,
        UserGroupKeys.name, UserGroupKeys.description, UserGroupKeys.memberIds, UserGroupKeys.appPermissions);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testListWithApplicationName() {
    when(featureFlagService.isEnabled(eq(FeatureName.SEARCH_USERGROUP_BY_APPLICATION), eq(accountId))).thenReturn(true);
    String applicationId = "applicationId";
    UserGroup userGroup1 = builder()
                               .uuid(userGroupId)
                               .name(userGroupName)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user1Id))
                               .members(asList(user))
                               .build();
    UserGroup savedUserGroup1 = userGroupService.save(userGroup1);
    AppPermission selectedApplicationFilter =
        AppPermission.builder()
            .appFilter(AppFilter.builder().filterType(SELECTED).ids(Collections.singleton(applicationId)).build())
            .build();
    UserGroup userGroup2 = builder()
                               .name(userGroupName3)
                               .uuid(userGroup2Id)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user2Id))
                               .members(asList(user))
                               .appPermissions(Sets.newHashSet(selectedApplicationFilter))
                               .build();
    UserGroup savedUserGroup2 = userGroupService.save(userGroup2);

    AppPermission allApplicationFilter =
        AppPermission.builder().appFilter(AppFilter.builder().filterType(ALL).build()).build();
    UserGroup userGroup3 = builder()
                               .name(userGroupName2)
                               .uuid(userGroup3Id)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user2Id))
                               .members(asList(user))
                               .appPermissions(Sets.newHashSet(allApplicationFilter))
                               .build();
    UserGroup savedUserGroup3 = userGroupService.save(userGroup3);
    when(appService.list(any()))
        .thenReturn(aPageResponse()
                        .withResponse(
                            Collections.singletonList(Application.Builder.anApplication().uuid(applicationId).build()))
                        .build());
    PageResponse pageResponse = userGroupService.list(accountId, PageRequestBuilder.aPageRequest().build(), true,
        UserGroupSearchTermType.APPLICATION_NAME, "application");
    assertThat(pageResponse).isNotNull();
    List<UserGroup> userGroupList = pageResponse.getResponse();
    assertThat(userGroupList).isNotNull();
    assertThat(userGroupList).hasSize(2);
    assertThat(userGroupList).containsExactlyInAnyOrder(savedUserGroup3, savedUserGroup2);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testList() {
    UserGroup userGroup1 = builder()
                               .uuid(userGroupId)
                               .name(userGroupName)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user1Id))
                               .members(asList(user))
                               .build();
    UserGroup savedUserGroup1 = userGroupService.save(userGroup1);

    UserGroup userGroup2 = builder()
                               .name(userGroupName2)
                               .uuid(userGroup2Id)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user2Id))
                               .members(asList(user))
                               .appPermissions(Sets.newHashSet(envPermission))
                               .build();
    UserGroup savedUserGroup2 = userGroupService.save(userGroup2);

    PageResponse pageResponse =
        userGroupService.list(accountId, PageRequestBuilder.aPageRequest().build(), true, null, null);
    assertThat(pageResponse).isNotNull();
    List<UserGroup> userGroupList = pageResponse.getResponse();
    assertThat(userGroupList).isNotNull();
    assertThat(userGroupList).hasSize(2);
    assertThat(userGroupList).containsExactlyInAnyOrder(savedUserGroup1, savedUserGroup2);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSearchListByContainOp() {
    UserGroup userGroup1 =
        UserGroup.builder().uuid("uuid1").accountId(accountId).name("usergroup").description(description).build();
    UserGroup userGroup2 =
        UserGroup.builder().uuid("uuid2").accountId(accountId).name("groupuser").description(description).build();
    UserGroup userGroup3 =
        UserGroup.builder().uuid("uuid3").accountId(accountId).name("user group test").description(description).build();
    UserGroup userGroup4 =
        UserGroup.builder().uuid("uuid4").accountId(accountId).name("user").description(description).build();

    userGroup1 = userGroupService.save(userGroup1);
    userGroup2 = userGroupService.save(userGroup2);
    userGroup3 = userGroupService.save(userGroup3);
    userGroup4 = userGroupService.save(userGroup4);

    SearchFilter searchFilter = SearchFilter.builder()
                                    .fieldName(UserGroupKeys.name)
                                    .op(SearchFilter.Operator.CONTAINS)
                                    .fieldValues(new String[] {"group"})
                                    .build();

    PageResponse pageResponse = userGroupService.list(
        accountId, PageRequestBuilder.aPageRequest().addFilter(searchFilter).build(), true, null, null);
    assertThat(pageResponse).isNotNull();
    List<UserGroup> userGroupList = pageResponse.getResponse();
    assertThat(userGroupList).isNotNull();
    assertThat(userGroupList).hasSize(3);
    assertThat(userGroupList).containsExactlyInAnyOrder(userGroup1, userGroup2, userGroup3);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testListByName() {
    UserGroup userGroup1 = builder()
                               .uuid(userGroupId)
                               .name(userGroupName)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user1Id))
                               .members(asList(user))
                               .build();
    UserGroup savedUserGroup1 = userGroupService.save(userGroup1);

    List<UserGroup> userGroups = userGroupService.listByName(accountId, singletonList(userGroupName));
    assertThat(userGroups).isNotNull();
    assertThat(userGroups).hasSize(1);
    assertThat(userGroups).containsExactlyInAnyOrder(savedUserGroup1);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldCloneUserGroup() {
    final UserGroup storedGroupToClone = builder()
                                             .uuid(USER_GROUP_ID)
                                             .appId(APP_ID)
                                             .createdBy(null)
                                             .createdAt(0)
                                             .lastUpdatedBy(null)
                                             .lastUpdatedAt(0)
                                             .entityYamlPath(null)
                                             .name("Stored")
                                             .description("Desc")
                                             .accountId(ACCOUNT_ID)
                                             .memberIds(null)
                                             .members(null)
                                             .appPermissions(null)
                                             .accountPermissions(null)
                                             .build();
    userGroupService.save(storedGroupToClone);
    final Account account = anAccount().withUuid(ACCOUNT_ID).build();
    doReturn(account).when(accountService).get(ACCOUNT_ID);
    final String newName = "NewName";
    final String newDescription = "Desc";
    doNothing().when(authService).evictUserPermissionAndRestrictionCacheForAccount(anyString(), any());
    UserGroup cloneActual = userGroupService.cloneUserGroup(ACCOUNT_ID, USER_GROUP_ID, newName, newDescription);
    assertThat(cloneActual.getName()).isEqualTo(newName);
    assertThat(cloneActual.getDescription()).isEqualTo(newDescription);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateUserGroup() {
    try (UserThreadLocal.Guard guard = userGuard(anUser().uuid(generateUuid()).build())) {
      ArgumentCaptor<EmailData> emailDataArgumentCaptor = ArgumentCaptor.forClass(EmailData.class);
      Account accountForUser = createAndSaveAccount(ACCOUNT_ID);
      User savedUser = createUserAndSave(
          "USER_ID_DUMMY", "USER_NAME", UserGroup.builder().uuid("USERGROUP_TEST").build(), accountForUser);
      AppFilter appFilter = AppFilter.builder().filterType(FilterType.ALL).build();
      AppPermission appPermission =
          AppPermission.builder().permissionType(PermissionType.ALL_APP_ENTITIES).appFilter(appFilter).build();

      NotificationSettings notificationSettings =
          new NotificationSettings(false, true, Collections.emptyList(), null, null, null);
      UserGroup userGroup1 = UserGroup.builder()
                                 .accountId(ACCOUNT_ID)
                                 .name("USER_GROUP1")
                                 .appPermissions(Sets.newHashSet(appPermission))
                                 .notificationSettings(notificationSettings)
                                 .build();
      UserGroup userGroup2 = UserGroup.builder()
                                 .accountId(ACCOUNT_ID)
                                 .name("USER_GROUP2")
                                 .appPermissions(Sets.newHashSet(appPermission))
                                 .notificationSettings(notificationSettings)
                                 .build();
      UserGroup userGroup3 = UserGroup.builder()
                                 .accountId(ACCOUNT_ID)
                                 .name("USER_GROUP3")
                                 .appPermissions(Sets.newHashSet(appPermission))
                                 .notificationSettings(notificationSettings)
                                 .build();

      userGroup1 = userGroupService.save(userGroup1);
      userGroup2 = userGroupService.save(userGroup2);
      userGroup3 = userGroupService.save(userGroup3);

      // Update operation 1
      User userAfterUpdate = userService.updateUserGroupsOfUser(
          savedUser.getUuid(), Arrays.asList(userGroup1, userGroup2), ACCOUNT_ID, true);
      verify(auditServiceHelper, atLeast(3))
          .reportForAuditingUsingAccountId(ArgumentCaptor.forClass(String.class).capture(),
              ArgumentCaptor.forClass(Object.class).capture(), ArgumentCaptor.forClass(Object.class).capture(),
              ArgumentCaptor.forClass(Type.class).capture());
      userGroup1 = userGroupService.get(ACCOUNT_ID, userGroup1.getUuid());
      userGroup2 = userGroupService.get(ACCOUNT_ID, userGroup2.getUuid());
      assertThat(userAfterUpdate.getUserGroups()).size().isEqualTo(2);
      assertThat(userAfterUpdate.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet()))
          .containsExactlyInAnyOrder(userGroup1.getUuid(), userGroup2.getUuid());

      verify(emailNotificationService, atLeastOnce()).send(emailDataArgumentCaptor.capture());
      List<EmailData> emailsData = emailDataArgumentCaptor.getAllValues();
      assertThat(emailsData.stream()
                     .filter(emailData -> emailData.getTemplateName().equals(INVITE_EMAIL_TEMPLATE_NAME))
                     .collect(toList())
                     .size()
          == 1)
          .isFalse();

      userGroup1 = userGroupService.get(ACCOUNT_ID, userGroup1.getUuid());
      userGroup2 = userGroupService.get(ACCOUNT_ID, userGroup2.getUuid());
      userGroup3 = userGroupService.get(ACCOUNT_ID, userGroup3.getUuid());
      assertThat(userGroup1.getMemberIds()).containsExactly(savedUser.getUuid());
      assertThat(userGroup2.getMemberIds()).containsExactly(savedUser.getUuid());
      assertThat(userGroup3.getMemberIds()).isNullOrEmpty();

      savedUser.setName("John Doe");
      userAfterUpdate = userService.updateUserGroupsOfUser(
          savedUser.getUuid(), Arrays.asList(userGroup1, userGroup3), ACCOUNT_ID, true);
      assertThat(userAfterUpdate.getUserGroups().size()).isEqualTo(2);
      assertThat(userAfterUpdate.getUserGroups().stream().map(UserGroup::getUuid).collect(Collectors.toSet()))
          .containsExactlyInAnyOrder(userGroup1.getUuid(), userGroup3.getUuid());

      userGroup1 = userGroupService.get(ACCOUNT_ID, userGroup1.getUuid());
      userGroup2 = userGroupService.get(ACCOUNT_ID, userGroup2.getUuid());
      userGroup3 = userGroupService.get(ACCOUNT_ID, userGroup3.getUuid());
      assertThat(userGroup1.getMemberIds()).containsExactly(savedUser.getUuid());
      assertThat(userGroup2.getMemberIds()).isNullOrEmpty();
      assertThat(userGroup3.getMemberIds()).containsExactly(savedUser.getUuid());
      verify(emailNotificationService, atLeastOnce()).send(emailDataArgumentCaptor.capture());
      emailsData = emailDataArgumentCaptor.getAllValues();
      assertThat(
          emailsData.stream()
              .filter(emailData -> emailData.getTemplateName().equals(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME))
              .collect(toList())
              .size()
          == 1)
          .isFalse();
    }
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void shouldUpdateUserGroupPermissions() {
    AppFilter appFilter = AppFilter.builder().filterType(FilterType.ALL).build();
    AppPermission appPermission =
        AppPermission.builder().permissionType(PermissionType.ALL_APP_ENTITIES).appFilter(appFilter).build();
    UserGroup userGroup = builder().accountId(accountId).name(userGroupName).build();
    UserGroup savedUserGroup = userGroupService.save(userGroup);

    Set<PermissionType> allPermissions =
        ImmutableSet.of(MANAGE_APPLICATIONS, USER_PERMISSION_READ, USER_PERMISSION_MANAGEMENT, TEMPLATE_MANAGEMENT,
            ACCOUNT_MANAGEMENT, AUDIT_VIEWER, MANAGE_TAGS, MANAGE_ACCOUNT_DEFAULTS);
    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(new HashSet<>(allPermissions)).build();
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(appPermission);
    UserGroup updatedUserGroup = userGroupService.setUserGroupPermissions(
        accountId, savedUserGroup.getUuid(), accountPermissions, appPermissions);
    assertThat(updatedUserGroup.getAccountPermissions().getPermissions())
        .containsExactlyInAnyOrderElementsOf(allPermissions);
    assertThat(updatedUserGroup.getAppPermissions()).containsExactlyInAnyOrderElementsOf(appPermissions);
  }

  @Test
  @Owner(developers = REETIKA)
  @Category(UnitTests.class)
  public void shouldValidateUserGroup() {
    UserGroup userGroup = builder().accountId(accountId).name(userGroupName).build();
    UserGroup savedUserGroup = userGroupService.save(userGroup);

    Set<PermissionType> allPermissions =
        ImmutableSet.of(MANAGE_APPLICATIONS, USER_PERMISSION_READ, USER_PERMISSION_MANAGEMENT, TEMPLATE_MANAGEMENT,
            ACCOUNT_MANAGEMENT, AUDIT_VIEWER, MANAGE_TAGS, MANAGE_ACCOUNT_DEFAULTS);
    AccountPermissions accountPermissions =
        AccountPermissions.builder().permissions(new HashSet<>(allPermissions)).build();
    Set<AppPermission> appPermissions = new HashSet<>();
    AppFilter appFilter = AppFilter.builder().filterType(AppFilter.FilterType.EXCLUDE_SELECTED).build();
    AppPermission appPermission =
        AppPermission.builder().permissionType(PermissionType.ALL_APP_ENTITIES).appFilter(appFilter).build();

    appPermissions.add(appPermission);
    try {
      userGroupService.setUserGroupPermissions(accountId, savedUserGroup.getUuid(), accountPermissions, appPermissions);
      fail("Expected failure as the appFilter is invalid");
    } catch (InvalidRequestException exception) {
      assertThat(exception.getParams().get("message"))
          .isEqualTo("Invalid Request: Please provide atleast one application");
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdateNotificationSettings() {
    UserGroup ug = builder().accountId(accountId).name("some-name").build();
    UserGroup saved = userGroupService.save(ug);
    UserGroup fetchedGroup = userGroupService.get(accountId, saved.getUuid(), false);
    assertThat(fetchedGroup).isNotNull();
    assertThat(fetchedGroup.getNotificationSettings()).isNotNull();

    NotificationSettings settings = new NotificationSettings(
        true, true, Collections.emptyList(), SlackNotificationSetting.emptyConfig(), null, null);
    userGroupService.updateNotificationSettings(accountId, fetchedGroup.getUuid(), settings);
    fetchedGroup = userGroupService.get(accountId, fetchedGroup.getUuid(), false);
    assertThat(fetchedGroup.getNotificationSettings()).isNotNull();
    assertThat(fetchedGroup.getNotificationSettings()).isEqualTo(settings);
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(accountId), eq(null), any(UserGroup.class), eq(Type.UPDATE_NOTIFICATION_SETTING));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void shouldUpdateMicrosoftTeamsWebhookUrl() {
    NotificationSettings notificationSettings =
        new NotificationSettings(true, true, Collections.emptyList(), SlackNotificationSetting.emptyConfig(), null, "");
    UserGroup ug = builder().accountId(accountId).notificationSettings(notificationSettings).name("some-name").build();
    UserGroup saved = userGroupService.save(ug);
    UserGroup fetchedGroup = userGroupService.get(accountId, saved.getUuid(), false);
    assertThat(fetchedGroup).isNotNull();
    assertThat(fetchedGroup.getNotificationSettings()).isEqualTo(notificationSettings);
    assertThat(fetchedGroup.getNotificationSettings().getMicrosoftTeamsWebhookUrl()).isEmpty();

    NotificationSettings newNotificationSettings = new NotificationSettings(
        true, true, Collections.emptyList(), SlackNotificationSetting.emptyConfig(), null, MICROSOFT_TEAMS_WEBHOOK_URL);
    userGroupService.updateNotificationSettings(accountId, fetchedGroup.getUuid(), newNotificationSettings);
    fetchedGroup = userGroupService.get(accountId, fetchedGroup.getUuid(), false);
    assertThat(fetchedGroup.getNotificationSettings()).isNotNull();
    assertThat(fetchedGroup.getNotificationSettings()).isEqualTo(newNotificationSettings);
    assertThat(fetchedGroup.getNotificationSettings().getMicrosoftTeamsWebhookUrl())
        .isEqualTo(MICROSOFT_TEAMS_WEBHOOK_URL);
    verify(auditServiceHelper, times(1))
        .reportForAuditingUsingAccountId(
            eq(accountId), eq(null), any(UserGroup.class), eq(Type.UPDATE_NOTIFICATION_SETTING));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void shouldUpdateOverview() {
    UserGroup ug = builder().accountId(accountId).name("some-name").build();
    UserGroup saved = userGroupService.save(ug);

    UserGroup fetchedGroup = userGroupService.get(accountId, saved.getUuid(), false);
    fetchedGroup.setDescription("something-new");
    userGroupService.updateOverview(fetchedGroup);
    compare(fetchedGroup, userGroupService.get(accountId, saved.getUuid()));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldUpdateMembers() {
    try (UserThreadLocal.Guard guard = userGuard(null)) {
      ArgumentCaptor<EmailData> emailDataArgumentCaptor = ArgumentCaptor.forClass(EmailData.class);
      when(accountService.save(any(), eq(false), eq(false))).thenReturn(account);
      when(accountService.get(ACCOUNT_ID)).thenReturn(account);

      AppFilter appFilter = AppFilter.builder().filterType(FilterType.ALL).build();
      AppPermission appPermission =
          AppPermission.builder().permissionType(PermissionType.ALL_APP_ENTITIES).appFilter(appFilter).build();
      UserGroup userGroup1 =
          builder().accountId(ACCOUNT_ID).name("USER_GROUP1").appPermissions(Sets.newHashSet(appPermission)).build();
      User user1 = anUser()
                       .appId(APP_ID)
                       .email("user1@wings.software")
                       .name(USER_NAME)
                       .password(PASSWORD)
                       .accountName(ACCOUNT_NAME)
                       .companyName(COMPANY_NAME)
                       .accounts(Lists.newArrayList(account))
                       .build();

      User user2 = anUser()
                       .appId(APP_ID)
                       .email("user2@wings.software")
                       .name(USER_NAME)
                       .password(PASSWORD)
                       .accountName(ACCOUNT_NAME)
                       .companyName(COMPANY_NAME)
                       .accounts(Lists.newArrayList(account))
                       .build();

      userGroup1 = userGroupService.save(userGroup1);
      try {
        user1 = userService.register(user1);
      } catch (IndexOutOfBoundsException e) {
        // Ignoring the primary account fetch failure
      }
      try {
        user2 = userService.register(user2);
      } catch (IndexOutOfBoundsException e) {
        // Ignoring the primary account fetch failure
      }
      userGroup1.setMemberIds(Arrays.asList(user1.getUuid(), user2.getUuid()));
      userGroupService.updateMembers(userGroup1, true, false);
      verify(emailNotificationService, atLeastOnce()).send(emailDataArgumentCaptor.capture());
      List<EmailData> emailsData = emailDataArgumentCaptor.getAllValues();
      assertThat(2).isEqualTo(
          (int) emailsData.stream()
              .filter(emailData -> emailData.getTemplateName().equals(ADD_TO_ACCOUNT_OR_GROUP_EMAIL_TEMPLATE_NAME))
              .count());
      userGroup1.setMemberIds(singletonList(user1.getUuid()));
      userGroupService.updateMembers(userGroup1, true, true);
      verify(auditServiceHelper, atLeast(2))
          .reportForAuditingUsingAccountId(ArgumentCaptor.forClass(String.class).capture(),
              ArgumentCaptor.forClass(Object.class).capture(), ArgumentCaptor.forClass(Object.class).capture(),
              ArgumentCaptor.forClass(Type.class).capture());
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testIsUserAuthorizedToAcceptOrRejectApproval() {
    User user = createUser("User");
    persistence.save(user);
    UserThreadLocal.set(user);

    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(ACCOUNT_ID, null)).isFalse();
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, null)).isFalse();

    UserGroup userGroup = createUserGroup(null);
    persistence.save(userGroup);
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(ACCOUNT_ID, asList(userGroup.getUuid())))
        .isFalse();
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, asList(userGroup.getUuid())))
        .isFalse();

    userGroup = createUserGroup(asList(user.getUuid()));
    persistence.save(userGroup);
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(ACCOUNT_ID, asList(userGroup.getUuid())))
        .isTrue();
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, asList(userGroup.getUuid())))
        .isTrue();

    User user1 = createUser("User1");
    persistence.save(user1);
    UserThreadLocal.set(user1);
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(ACCOUNT_ID, asList(userGroup.getUuid())))
        .isFalse();
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, asList(userGroup.getUuid())))
        .isFalse();

    user.setEmailVerified(false);
    UserThreadLocal.set(user);
    assertThat(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(ACCOUNT_ID, asList(userGroup.getUuid())))
        .isFalse();
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldNotDeleteAdminUserGroup() {
    UserGroup defaultUserGroup =
        builder().accountId(ACCOUNT_ID).name(DEFAULT_ACCOUNT_ADMIN_USER_GROUP_NAME).isDefault(true).build();
    persistence.save(defaultUserGroup);

    boolean deleted = userGroupService.delete(ACCOUNT_ID, defaultUserGroup.getUuid(), false);
    assertThat(deleted).isFalse();
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void shouldDeleteNonAdminUserGroup() {
    UserGroup nonDefaultUserGroup =
        builder().accountId(ACCOUNT_ID).name(DEFAULT_READ_ONLY_USER_GROUP_NAME).isDefault(true).build();
    persistence.save(nonDefaultUserGroup);

    boolean deleted = userGroupService.delete(ACCOUNT_ID, nonDefaultUserGroup.getUuid(), false);

    assertThat(deleted).isTrue();
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testUserGroups() {
    UserGroup defaultUserGroup = builder()
                                     .accountId(ACCOUNT_ID)
                                     .name(userGroupName)
                                     .memberIds(singletonList(user.getUuid()))
                                     .isDefault(true)
                                     .build();
    UserGroup nonDefaultUserGroup = builder()
                                        .accountId(ACCOUNT_ID)
                                        .name(userGroupName2)
                                        .memberIds(singletonList(user.getUuid()))
                                        .isDefault(false)
                                        .build();

    persistence.save(defaultUserGroup);
    persistence.save(nonDefaultUserGroup);

    assertThat(getIds(userGroupService.listByAccountId(ACCOUNT_ID, user, true)))
        .isEqualTo(Arrays.asList(defaultUserGroup.getUuid(), nonDefaultUserGroup.getUuid()));
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testFilter() {
    UserGroup userGroupAccountId =
        builder().accountId(ACCOUNT_ID).name(userGroupName).memberIds(singletonList(user.getUuid())).build();
    UserGroup userGroupAccountId1 =
        builder().accountId(ACCOUNT1_ID).name(userGroupName2).memberIds(singletonList(user.getUuid())).build();
    persistence.save(userGroupAccountId);
    persistence.save(userGroupAccountId1);

    List<UserGroup> userGroups = userGroupService.filter(ACCOUNT_ID, null);
    assertThat(userGroups.size()).isEqualTo(1);
    assertThat(userGroups.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testFilterByUserGroupId() {
    UserGroup userGroupUuid = builder().uuid(UUID).accountId(ACCOUNT_ID).name(userGroupName).build();
    UserGroup userGroupUuid1 = builder().uuid(UUID1).accountId(ACCOUNT_ID).name(userGroupName2).build();
    persistence.save(userGroupUuid);
    persistence.save(userGroupUuid1);

    List<UserGroup> userGroups = userGroupService.filter(ACCOUNT_ID, singletonList(UUID));
    assertThat(userGroups.size()).isEqualTo(1);
    assertThat(userGroups.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(userGroups.get(0).getUuid()).isEqualTo(UUID);
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testFilterWithJustNameInResponse() {
    UserGroup userGroupUuid =
        builder().uuid(UUID).accountId(ACCOUNT_ID).name(userGroupName).memberIds(singletonList(user.getUuid())).build();
    UserGroup userGroupUuid1 = builder()
                                   .uuid(UUID1)
                                   .accountId(ACCOUNT1_ID)
                                   .name(userGroupName2)
                                   .memberIds(singletonList(user.getUuid()))
                                   .build();
    persistence.save(userGroupUuid);
    persistence.save(userGroupUuid1);

    List<UserGroup> userGroups = userGroupService.filter(ACCOUNT_ID, null, singletonList("name"));
    assertThat(userGroups.size()).isEqualTo(1);
    assertThat(userGroups.get(0).getAccountId()).isNull();
    assertThat(userGroups.get(0).getMemberIds()).isNull();
    assertThat(userGroups.get(0).getName()).isEqualTo(userGroupName);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFetchUserGroupNamesFromIdsUsingSecondary() {
    List<UserGroup> userGroups = userGroupService.fetchUserGroupNamesFromIdsUsingSecondary(null);
    assertThat(userGroups).isNotNull();
    assertThat(userGroups).isEmpty();

    UserGroup userGroup1 = UserGroup.builder().accountId(ACCOUNT_ID).name("USER_GROUP1").build();
    UserGroup userGroup2 = UserGroup.builder().accountId(ACCOUNT_ID).name("USER_GROUP2").build();
    UserGroup userGroup3 = UserGroup.builder().accountId(ACCOUNT_ID).name("USER_GROUP3").build();

    userGroup1 = userGroupService.save(userGroup1);
    userGroup2 = userGroupService.save(userGroup2);
    userGroup3 = userGroupService.save(userGroup3);

    userGroups = userGroupService.fetchUserGroupNamesFromIdsUsingSecondary(
        asList(userGroup1.getUuid(), userGroup2.getUuid(), userGroup3.getUuid()));
    assertThat(userGroups).isNotNull();
    assertThat(userGroups.stream().map(UserGroup::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("USER_GROUP1", "USER_GROUP2", "USER_GROUP3");
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = NIKOLA)
  @Category(UnitTests.class)
  public void shouldNotAllowCreatingUserGroupWithoutName() {
    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name("")
                              .appPermissions(Sets.newHashSet(envPermission))
                              .memberIds(asList(user1Id, user2Id))
                              .build();

    UserGroup savedUserGroup = userGroupService.save(userGroup);
    assertThat(savedUserGroup).isNull();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void shouldNotSaveUserGroupWithInvalidName() {
    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name("<a href='http://authorization.site'>Click ME</a>")
                              .appPermissions(Sets.newHashSet(envPermission))
                              .memberIds(asList(user1Id, user2Id))
                              .build();

    userGroupService.save(userGroup);
  }

  @Test(expected = UserGroupAlreadyExistException.class)
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldNotAllowDuplicateUserGroups_tc1() {
    UserGroup userGroup1 =
        builder().accountId(accountId).uuid(userGroupId).description(description).name(userGroupName).build();
    persistence.save(userGroup1);
    UserGroup userGroup2 =
        builder().accountId(accountId).description(description).name(userGroupName).description(description).build();
    userGroupService.save(userGroup2);
  }

  @Test(expected = UserGroupAlreadyExistException.class)
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldNotAllowDuplicateUserGroups_tc2() {
    UserGroup userGroup1 =
        builder().accountId(accountId).uuid(userGroupId).description(description).name(userGroupName).build();
    UserGroup userGroup2 =
        builder().accountId(accountId).uuid(userGroup2Id).description(description).name(userGroupName2).build();
    persistence.save(userGroup1);
    persistence.save(userGroup2);
    userGroup2.setName(userGroupName);
    userGroupService.updateOverview(userGroup2);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testPruneByApplication() {
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(createAppPermission(FilterType.SELECTED, PermissionType.ALL_APP_ENTITIES,
        Arrays.asList("111", "222", "333"), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.ALL_APP_ENTITIES, Arrays.asList("444"), Collections.emptySet()));

    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name(userGroupName)
                              .appPermissions(appPermissions)
                              .build();

    persistence.save(userGroup);
    when(appService.getAccountIdByAppId(any())).thenReturn(accountId);
    userGroupService.pruneByApplication("222");

    UserGroup prunedGroup = persistence.createQuery(UserGroup.class, excludeAuthority)
                                .filter(UserGroupKeys.accountId, accountId)
                                .filter(UserGroup.ID_KEY2, userGroupId)
                                .get();

    Set<String> prunedAppIds = getAppIds(prunedGroup);

    assertThat(prunedGroup.getAppPermissions().size()).isEqualTo(2);
    assertThat(prunedAppIds.size()).isEqualTo(3);
    assertThat(prunedAppIds.containsAll(Arrays.asList("111", "333", "444"))).isTrue();
    assertThat(prunedAppIds.contains("222")).isFalse();
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void createQueryForUserGroup_accountIdIsNull() {
    String appId = "appId1";
    Query<UserGroup> expectedQuery = persistence.createQuery(UserGroup.class)
                                         .field(UserGroupKeys.appIds)
                                         .in(Set.of(appId))
                                         .project(UserGroup.ID_KEY2, true)
                                         .project(UserGroupKeys.accountId, true)
                                         .project(UserGroupKeys.appPermissions, true)
                                         .project(UserGroupKeys.memberIds, true);
    Query<UserGroup> query = userGroupService.createQueryForUserGroup(null, Set.of(appId));
    assertThat(query).isEqualTo(expectedQuery);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void createQueryForUserGroup_accountIdNotNull() {
    String appId = "appId1";
    Query<UserGroup> expectedQuery = persistence.createQuery(UserGroup.class)
                                         .filter(UserGroupKeys.accountId, accountId)
                                         .project(UserGroup.ID_KEY2, true)
                                         .project(UserGroupKeys.accountId, true)
                                         .project(UserGroupKeys.appPermissions, true)
                                         .project(UserGroupKeys.memberIds, true);
    Query<UserGroup> query = userGroupService.createQueryForUserGroup(accountId, Set.of(appId));
    assertThat(query).isEqualTo(expectedQuery);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testRemoveEmptyAppPermissions() {
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.WORKFLOW, Arrays.asList("111", "222", "333"), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.ACCOUNT_MANAGEMENT, Collections.emptyList(), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.DEPLOYMENT, Collections.emptyList(), Collections.emptySet()));
    assertThat(appPermissions.size()).isEqualTo(3);

    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name(userGroupName)
                              .appPermissions(appPermissions)
                              .build();

    persistence.save(userGroup);
    when(appService.getAccountIdByAppId(any())).thenReturn(accountId);
    userGroupService.pruneByApplication("444");

    UserGroup prunedGroup = persistence.createQuery(UserGroup.class, excludeAuthority)
                                .filter(UserGroupKeys.accountId, accountId)
                                .filter(UserGroup.ID_KEY2, userGroupId)
                                .get();

    Set<String> appIds = getAppIds(prunedGroup);

    assertThat(prunedGroup.getAppPermissions().size()).isEqualTo(1);
    assertThat(appIds.containsAll(Arrays.asList("111", "222", "333"))).isTrue();
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testPruneByApplicationAllPermissionsRemoved() {
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.WORKFLOW, singletonList("111"), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.ACCOUNT_MANAGEMENT, Collections.emptyList(), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.DEPLOYMENT, Collections.emptyList(), Collections.emptySet()));

    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name(userGroupName)
                              .appPermissions(appPermissions)
                              .build();

    persistence.save(userGroup);
    when(appService.getAccountIdByAppId(any())).thenReturn(accountId);
    userGroupService.pruneByApplication("111");

    UserGroup prunedGroup = persistence.createQuery(UserGroup.class, excludeAuthority)
                                .filter(UserGroupKeys.accountId, accountId)
                                .filter(UserGroup.ID_KEY2, userGroupId)
                                .get();

    assertThat(prunedGroup.getAppPermissions()).isNull();
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testPruneByApplicationNotSelectedPermissionsAreNotRemoved() {
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(createAppPermission(FilterType.ALL, PermissionType.WORKFLOW, null, Collections.emptySet()));
    appPermissions.add(
        createAppPermission(FilterType.ALL, PermissionType.ACCOUNT_MANAGEMENT, null, Collections.emptySet()));
    appPermissions.add(createAppPermission(FilterType.ALL, PermissionType.DEPLOYMENT, null, Collections.emptySet()));

    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name(userGroupName)
                              .appPermissions(appPermissions)
                              .build();

    persistence.save(userGroup);

    userGroupService.pruneByApplication("111");

    UserGroup prunedGroup = persistence.createQuery(UserGroup.class, excludeAuthority)
                                .filter(UserGroupKeys.accountId, accountId)
                                .filter(UserGroup.ID_KEY2, userGroupId)
                                .get();

    assertThat(prunedGroup.getAppPermissions().size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void testRemoveDeletedAppIds() {
    Set<AppPermission> appPermissions = new HashSet<>();
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.WORKFLOW, Arrays.asList("111", "222", "333"), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.ACCOUNT_MANAGEMENT, Collections.emptyList(), Collections.emptySet()));
    appPermissions.add(createAppPermission(
        FilterType.SELECTED, PermissionType.DEPLOYMENT, Collections.emptyList(), Collections.emptySet()));
    assertThat(appPermissions.size()).isEqualTo(3);

    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid(userGroupId)
                              .description(description)
                              .name(userGroupName)
                              .appPermissions(appPermissions)
                              .build();

    persistence.save(userGroup);

    Set<String> nonExistingAppIds = new HashSet(Arrays.asList("111", "333", "444", "555", "666"));

    userGroupService.removeAppIdsFromAppPermissions(userGroup, nonExistingAppIds);

    UserGroup cleanedGroup = persistence.createQuery(UserGroup.class, excludeAuthority)
                                 .filter(UserGroupKeys.accountId, accountId)
                                 .filter(UserGroup.ID_KEY2, userGroupId)
                                 .get();

    Set<String> appIds = getAppIds(cleanedGroup);

    assertThat(cleanedGroup.getAppPermissions().size()).isEqualTo(1);
    assertThat(appIds.size()).isEqualTo(1);
    assertThat(appIds.containsAll(Arrays.asList("222"))).isTrue();
  }

  private List<AppPermission> getTemplateAppPermissions(Set<AppPermission> appPermissions) {
    if (isEmpty(appPermissions)) {
      return new ArrayList<>();
    }
    return appPermissions.stream()
        .filter(appPermission
            -> appPermission.getPermissionType() != null && appPermission.getPermissionType().equals(APP_TEMPLATE))
        .collect(toList());
  }

  private void verifyApplicationTemplatePermissionsWithAllFilter(AppPermission templateAppPermission) {
    assertThat(templateAppPermission.getPermissionType()).isEqualTo(APP_TEMPLATE);
    assertThat(templateAppPermission.getAppFilter().getFilterType()).isEqualTo(FilterType.ALL);
  }

  private void verifyApplicationTemplatePermissionsWithSelectedFilter(AppPermission templateAppPermission) {
    assertThat(templateAppPermission.getPermissionType()).isEqualTo(APP_TEMPLATE);
    assertThat(templateAppPermission.getAppFilter().getFilterType()).isEqualTo(FilterType.SELECTED);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_save_TC1() {
    // User lacks TEMPLATE_MANAGEMENT Account Permission but has Application Permission on all applications
    final UserGroup userGroup =
        UserGroup.builder()
            .accountId(accountId)
            .uuid(userGroupId)
            .name("Test User Group")
            .accountPermissions(
                AccountPermissions.builder().permissions(new HashSet<>(Arrays.asList(ACCOUNT_MANAGEMENT))).build())
            .appPermissions(new HashSet<>(
                Arrays.asList(createAppPermission(FilterType.ALL, ALL_APP_ENTITIES, Collections.emptyList(), actions))))
            .build();
    final UserGroup savedUserGroup = userGroupService.save(userGroup);

    final Set<AppPermission> appPermissions = savedUserGroup.getAppPermissions();
    assertThat(appPermissions.size()).isEqualTo(1);
    final AppPermission appPermission = appPermissions.iterator().next();
    assertThat(appPermission).isNotNull();
    assertThat(appPermission.getPermissionType()).isEqualTo(ALL_APP_ENTITIES);
    assertThat(appPermission.getAppFilter().getFilterType()).isEqualTo(FilterType.ALL);
    assertThat(appPermission.getActions().size()).isEqualTo(4);
    assertThat(appPermission.getActions().containsAll(actions)).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_save_TC2() {
    // User has TEMPLATE_MANAGEMENT Account Permission and Application Permission on all applications
    final UserGroup userGroup =
        UserGroup.builder()
            .accountId(accountId)
            .uuid(userGroupId)
            .name("Test User Group")
            .accountPermissions(AccountPermissions.builder()
                                    .permissions(new HashSet<>(Arrays.asList(ACCOUNT_MANAGEMENT, TEMPLATE_MANAGEMENT)))
                                    .build())
            .appPermissions(new HashSet<>(
                Arrays.asList(createAppPermission(FilterType.ALL, ALL_APP_ENTITIES, Collections.emptyList(), actions))))
            .build();
    final UserGroup savedUserGroup = userGroupService.save(userGroup);

    final Set<AppPermission> appPermissions = savedUserGroup.getAppPermissions();
    assertThat(appPermissions.size()).isEqualTo(1);
    final AppPermission appPermission = appPermissions.iterator().next();
    assertThat(appPermission).isNotNull();
    assertThat(appPermission.getPermissionType()).isEqualTo(ALL_APP_ENTITIES);
    assertThat(appPermission.getAppFilter().getFilterType()).isEqualTo(FilterType.ALL);
    assertThat(appPermission.getActions().size()).isEqualTo(4);
    assertThat(appPermission.getActions().containsAll(actions)).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_save_TC3() {
    // User lacks TEMPLATE_MANAGEMENT Account Permission but has Application Permission on some applications
    doReturn(appIds).when(appService).getAppIdsByAccountId(ACCOUNT_ID);
    final UserGroup userGroup =
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .uuid(userGroupId)
            .name("Test User Group")
            .accountPermissions(
                AccountPermissions.builder().permissions(new HashSet<>(Arrays.asList(ACCOUNT_MANAGEMENT))).build())
            .appPermissions(new HashSet<>(Arrays.asList(
                createAppPermission(FilterType.SELECTED, ALL_APP_ENTITIES, Arrays.asList("appId1"), actions))))
            .build();
    final UserGroup savedUserGroup = userGroupService.save(userGroup);

    final Set<AppPermission> appPermissions = savedUserGroup.getAppPermissions();
    assertThat(appPermissions.size()).isEqualTo(1);
    final AppPermission appPermission = appPermissions.iterator().next();
    assertThat(appPermission).isNotNull();
    assertThat(appPermission.getPermissionType()).isEqualTo(ALL_APP_ENTITIES);
    assertThat(appPermission.getAppFilter().getFilterType()).isEqualTo(FilterType.SELECTED);
    assertThat(appPermission.getAppFilter().getIds().containsAll(Arrays.asList("appId1"))).isEqualTo(true);
    assertThat(appPermission.getActions().size()).isEqualTo(4);
    assertThat(appPermission.getActions().containsAll(actions)).isEqualTo(true);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_save_TC4() {
    // User has TEMPLATE_MANAGEMENT Account Permission and Application Permission on some applications
    doReturn(appIds).when(appService).getAppIdsByAccountId(ACCOUNT_ID);
    final UserGroup userGroup =
        UserGroup.builder()
            .accountId(ACCOUNT_ID)
            .uuid(userGroupId)
            .name("Test User Group")
            .accountPermissions(AccountPermissions.builder()
                                    .permissions(new HashSet<>(Arrays.asList(ACCOUNT_MANAGEMENT, TEMPLATE_MANAGEMENT)))
                                    .build())
            .appPermissions(new HashSet<>(Arrays.asList(
                createAppPermission(FilterType.SELECTED, ALL_APP_ENTITIES, Arrays.asList("appId1"), actions))))
            .build();
    userGroupService.save(userGroup);

    final UserGroup savedUserGroup = userGroupService.save(userGroup);

    final Set<AppPermission> appPermissions = savedUserGroup.getAppPermissions();
    assertThat(appPermissions.size()).isEqualTo(1);
    final AppPermission appPermission = appPermissions.iterator().next();
    assertThat(appPermission).isNotNull();
    assertThat(appPermission.getPermissionType()).isEqualTo(ALL_APP_ENTITIES);
    assertThat(appPermission.getAppFilter().getFilterType()).isEqualTo(FilterType.SELECTED);
    assertThat(appPermission.getAppFilter().getIds().containsAll(Arrays.asList("appId1"))).isEqualTo(true);
    assertThat(appPermission.getActions().size()).isEqualTo(4);
    assertThat(appPermission.getActions().containsAll(actions)).isEqualTo(true);
  }

  private void verifyNoApplicationTemplatePermissions(Set<PermissionType> permissions) {
    final UserGroup userGroup = UserGroup.builder()
                                    .accountId(ACCOUNT_ID)
                                    .uuid(userGroupId)
                                    .name("Test User Group")
                                    .accountPermissions(AccountPermissions.builder().permissions(permissions).build())
                                    .appPermissions(null)
                                    .build();
    userGroupService.save(userGroup);

    final Set<AppPermission> appPermissions = userGroup.getAppPermissions();
    assertThat(appPermissions).isNullOrEmpty();
    final List<AppPermission> templateAppPermissions = getTemplateAppPermissions(appPermissions);
    assertThat(templateAppPermissions).isNullOrEmpty();
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_save_TC5() {
    // User lacks TEMPLATE_MANAGEMENT Account Permission and has no Application Permissions as well
    doReturn(appIds).when(appService).getAppIdsByAccountId(ACCOUNT_ID);
    verifyNoApplicationTemplatePermissions(new HashSet<>(Arrays.asList(ACCOUNT_MANAGEMENT)));
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_save_TC6() {
    // User has TEMPLATE_MANAGEMENT Account Permission and but no Application Permissions
    doReturn(appIds).when(appService).getAppIdsByAccountId(ACCOUNT_ID);
    verifyNoApplicationTemplatePermissions(new HashSet<>(Arrays.asList(ACCOUNT_MANAGEMENT, TEMPLATE_MANAGEMENT)));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_addParentsReferenceWithExistingParents() {
    UserGroupEntityReference reference1 = UserGroupEntityReference.builder()
                                              .id("pipelineId1")
                                              .accountId(accountId)
                                              .appId("appId1")
                                              .entityType("PIPELINE")
                                              .build();
    UserGroupEntityReference reference2 = UserGroupEntityReference.builder()
                                              .id("pipelineId2")
                                              .accountId(accountId)
                                              .appId("appId2")
                                              .entityType("PIPELINE")
                                              .build();
    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid("userGroupId")
                              .description(description)
                              .name(userGroupName)
                              .parents(new HashSet<>(Arrays.asList(reference1, reference2)))
                              .build();

    persistence.save(userGroup);
    UserGroupEntityReference reference3 = UserGroupEntityReference.builder()
                                              .id("pipelineId3")
                                              .accountId(accountId)
                                              .appId("appId3")
                                              .entityType("PIPELINE")
                                              .build();

    userGroupService.addParentsReference("userGroupId", accountId, "appId3", "pipelineId3", EntityType.PIPELINE.name());
    UserGroup updatedUserGroup = persistence.get(UserGroup.class, "userGroupId");
    assertThat(updatedUserGroup.getParents().size()).isEqualTo(3);
    assertThat(updatedUserGroup.getParents())
        .isEqualTo(new HashSet<>(Arrays.asList(reference1, reference2, reference3)));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_addParentsReferenceWithoutExistingParents() {
    UserGroup userGroup =
        builder().accountId(accountId).uuid("userGroupId").description(description).name(userGroupName).build();

    persistence.save(userGroup);
    UserGroupEntityReference reference = UserGroupEntityReference.builder()
                                             .id("pipelineId")
                                             .accountId(accountId)
                                             .appId("appId")
                                             .entityType("PIPELINE")
                                             .build();

    userGroupService.addParentsReference("userGroupId", accountId, "appId", "pipelineId", EntityType.PIPELINE.name());
    UserGroup updatedUserGroup = persistence.get(UserGroup.class, "userGroupId");
    assertThat(updatedUserGroup.getParents().size()).isEqualTo(1);
    assertThat(updatedUserGroup.getParents()).isEqualTo(new HashSet<>(Collections.singleton(reference)));
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_removeParentsReference() {
    UserGroupEntityReference reference = UserGroupEntityReference.builder()
                                             .id("pipelineId")
                                             .accountId(accountId)
                                             .appId("appId")
                                             .entityType("PIPELINE")
                                             .build();
    UserGroup userGroup = builder()
                              .accountId(accountId)
                              .uuid("userGroupId")
                              .description(description)
                              .name(userGroupName)
                              .parents(new HashSet<>(Collections.singleton(reference)))
                              .build();

    persistence.save(userGroup);

    userGroupService.removeParentsReference(
        "userGroupId", accountId, "appId", "pipelineId", EntityType.PIPELINE.name());
    UserGroup updatedUserGroup = persistence.get(UserGroup.class, "userGroupId");
    assertThat(updatedUserGroup.getParents().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_removeParentsReferenceWithoutHavingAnExistingReference() {
    UserGroup userGroup =
        builder().accountId(accountId).uuid("userGroupId").description(description).name(userGroupName).build();

    persistence.save(userGroup);

    userGroupService.removeParentsReference(
        "userGroupId", accountId, "appId", "pipelineId", EntityType.PIPELINE.name());
    UserGroup updatedUserGroup = persistence.get(UserGroup.class, "userGroupId");
    assertThat(updatedUserGroup.getParents().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_deleteUserGroupWithExistingReference() {
    UserGroup userGroup =
        builder().accountId(accountId).uuid("userGroupId").description(description).name(userGroupName).build();

    persistence.save(userGroup);
    UserGroupEntityReference reference = UserGroupEntityReference.builder()
                                             .id("pipelineId")
                                             .accountId(accountId)
                                             .appId("appId")
                                             .entityType("PIPELINE")
                                             .build();

    userGroupService.addParentsReference("userGroupId", accountId, "appId", "pipelineId", EntityType.PIPELINE.name());
    try {
      userGroupService.delete(accountId, "userGroupId", false);
    } catch (InvalidRequestException ex) {
      assertThat(ex.getMessage())
          .isEqualTo(
              "This userGroup is being referenced in either approval step/stage or notification strategy or as a runtimeInput in an execution step/stage or in a deployment freeze window. Please make sure to remove the references to delete this userGroup.");
    }
  }

  @Test()
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void test_getCountOfUserGroupsWithNullAccountId() {
    try {
      userGroupService.getCountOfUserGroups(null);
      fail("Expected failure as accountID is null");
    } catch (GeneralException exception) {
      assertThat(exception.getParams().get("message")).isEqualTo(UserGroupKeys.accountId);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void test_getCountOfUserGroupsWithNonNullAccountId() {
    UserGroup userGroup1 = builder()
                               .uuid(userGroupId)
                               .name(userGroupName)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user1Id))
                               .members(asList(user))
                               .build();
    UserGroup savedUserGroup1 = userGroupService.save(userGroup1);

    UserGroup userGroup2 = builder()
                               .name(userGroupName2)
                               .uuid(userGroup2Id)
                               .accountId(accountId)
                               .description(description)
                               .memberIds(asList(user2Id))
                               .members(asList(user))
                               .appPermissions(Sets.newHashSet(envPermission))
                               .build();
    UserGroup savedUserGroup2 = userGroupService.save(userGroup2);

    long count = userGroupService.getCountOfUserGroups(accountId);
    assertThat(count).isEqualTo(2);
  }

  private Set<String> getAppIds(UserGroup userGroup) {
    return userGroup.getAppPermissions()
        .stream()
        .map(AppPermission::getAppFilter)
        .map(Filter::getIds)
        .filter(EmptyPredicate::isNotEmpty)
        .reduce(new HashSet<>(), (a, b) -> {
          a.addAll(b);
          return a;
        });
  }

  private List<String> getIds(List<UserGroup> userGroups) {
    List<String> ids = new ArrayList<>();
    for (UserGroup userGroup : userGroups) {
      String uuid = userGroup.getUuid();
      ids.add(uuid);
    }
    return ids;
  }

  private User createUser(String userId) {
    Account account = Builder.anAccount()
                          .withUuid(ACCOUNT_ID)
                          .withCompanyName(COMPANY_NAME)
                          .withAccountName(ACCOUNT_NAME)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    return anUser().uuid(userId).appId(APP_ID).emailVerified(true).email(USER_EMAIL).accounts(asList(account)).build();
  }

  private AppPermission createAppPermission(
      String filterType, PermissionType permissionType, List<String> appIds, Set<Action> actions) {
    Set<String> ids = new HashSet<>();
    if (isNotEmpty(appIds)) {
      ids.addAll(appIds);
    }
    return new AppPermission(permissionType, new AppFilter(ids, filterType), new EnvFilter(), actions);
  }

  private UserGroup createUserGroup(List<String> memberIds) {
    return builder().accountId(ACCOUNT_ID).uuid(USER_GROUP_ID).memberIds(memberIds).build();
  }
}
