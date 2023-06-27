/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.AKRITI;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SHUBHANSHU;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.AUDIT_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.CE_ADMIN;
import static software.wings.security.PermissionAttribute.PermissionType.CE_VIEWER;
import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ACCOUNT_DEFAULTS;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_ALERT_NOTIFICATION_RULES;
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
import static software.wings.security.PermissionAttribute.PermissionType.TEMPLATE_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.Event;
import software.wings.beans.User;
import software.wings.resources.AccountResource;
import software.wings.resources.UserResourceNG;
import software.wings.resources.graphql.GraphQLUtils;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.WhitelistService;
import software.wings.utils.DummyTestResource;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
@TargetModule(HarnessModule.UNDEFINED)
public class AuthRuleFilterTest extends WingsBaseTest {
  private ResourceInfo resourceInfo = mock(ResourceInfo.class);
  @Mock HttpServletRequest httpServletRequest;
  @Mock AuthService authService;
  @Mock AuthHandler authHandler;
  @Mock AccountService accountService;
  @Mock UserService userService;
  @Mock AppService appService;
  @Mock WhitelistService whitelistService;
  @Mock HarnessUserGroupService harnessUserGroupService;
  @Mock GraphQLUtils graphQLUtils;
  @Mock ContainerRequestContext requestContext;
  @Mock UriInfo uriInfo;
  @Mock private AuditServiceHelper auditServiceHelper;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Mock ApiKeyService apiKeyService;

  @Inject @InjectMocks AuthRuleFilter authRuleFilter;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String APP_ID = "APP_ID";
  private static final String PATH = "PATH";
  private static final String USER_ID = "USER_ID";
  private static final String USERNAME = "USERNAME";
  private static final String API_KEY = "API_KEY";

  @Before
  public void setUp() throws IOException {
    initMocks(this);
    UserThreadLocal.set(mockUser(true));
    when(authHandler.getAllAccountPermissions())
        .thenReturn(Sets.newHashSet(USER_PERMISSION_MANAGEMENT, ACCOUNT_MANAGEMENT, MANAGE_APPLICATIONS,
            TEMPLATE_MANAGEMENT, USER_PERMISSION_READ, AUDIT_VIEWER, MANAGE_TAGS, MANAGE_ACCOUNT_DEFAULTS, CE_ADMIN,
            CE_VIEWER, MANAGE_CLOUD_PROVIDERS, MANAGE_CONNECTORS, MANAGE_APPLICATION_STACKS, MANAGE_DELEGATES,
            MANAGE_ALERT_NOTIFICATION_RULES, MANAGE_DELEGATE_PROFILES, MANAGE_CONFIG_AS_CODE, MANAGE_SECRETS,
            MANAGE_SECRET_MANAGERS, MANAGE_AUTHENTICATION_SETTINGS, MANAGE_IP_WHITELIST, MANAGE_DEPLOYMENT_FREEZES,
            MANAGE_PIPELINE_GOVERNANCE_STANDARDS, MANAGE_SSH_AND_WINRM, MANAGE_RESTRICTED_ACCESS));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testIsAccountLevelPermission() {
    PermissionAttribute permissionAttribute = new PermissionAttribute(PermissionType.AUDIT_VIEWER, Action.READ);
    PermissionAttribute permissionAttribute1 = new PermissionAttribute(PermissionType.APP, Action.ALL);
    assertThat(authRuleFilter.isAccountLevelPermissions(Arrays.asList(permissionAttribute, permissionAttribute1)))
        .isTrue();

    permissionAttribute = new PermissionAttribute(PermissionType.APP, Action.READ);
    assertThat(authRuleFilter.isAccountLevelPermissions(Arrays.asList(permissionAttribute))).isFalse();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFilterInAuthRuleFilter() {
    Set<Action> actions = new HashSet<>();
    actions.add(Action.DEFAULT);
    when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
    when(requestContext.getMethod()).thenReturn("GET");
    mockUriInfo(PATH, uriInfo);
    when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    when(harnessUserGroupService.isHarnessSupportEnabled(ACCOUNT_ID, USER_ID)).thenReturn(true);
    when(whitelistService.isValidIPAddress(any(), any())).thenReturn(true);
    when(authService.getUserPermissionInfo(any(), any(), anyBoolean())).thenReturn(mockUserPermissionInfo());
    authRuleFilter.filter(requestContext);
    assertThat(requestContext.getMethod()).isEqualTo("GET");
  }

  @Test
  @Owner(developers = AKRITI)
  @Category(UnitTests.class)
  public void testAuditWhitelistedIPs() {
    try {
      Set<Action> actions = new HashSet<>();
      actions.add(Action.DEFAULT);
      when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
      when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
      when(requestContext.getMethod()).thenReturn("GET");
      mockUriInfo(PATH, uriInfo);
      when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
      when(harnessUserGroupService.isHarnessSupportEnabledForAccount(ACCOUNT_ID)).thenReturn(true);
      when(whitelistService.isValidIPAddress(anyString(), anyString())).thenReturn(true);
      authRuleFilter.filter(requestContext);
    } catch (Exception e) {
    } finally {
      verify(auditServiceHelper, times(0))
          .reportForAuditingUsingAccountId(eq(ACCOUNT_ID), eq(null), any(User.class), eq(Event.Type.NON_WHITELISTED));
    }
  }

  @Test
  @Owner(developers = AKRITI)
  @Category(UnitTests.class)
  public void testAuditNonWhitelistedIPs() {
    try {
      Set<Action> actions = new HashSet<>();
      actions.add(Action.DEFAULT);
      when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
      when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
      when(requestContext.getMethod()).thenReturn("GET");
      mockUriInfo("whitelist/isEnabled", uriInfo);
      when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
      when(harnessUserGroupService.isHarnessSupportEnabledForAccount(ACCOUNT_ID)).thenReturn(true);
      authRuleFilter.filter(requestContext);
    } catch (Exception e) {
    } finally {
      verify(auditServiceHelper, times(0))
          .reportForAuditingUsingAccountId(eq(ACCOUNT_ID), eq(null), any(), eq(Event.Type.INVOKED));
    }
  }

  @Test
  @Owner(developers = AKRITI)
  @Category(UnitTests.class)
  public void testAuditAPIKeyInvoked() {
    try {
      Set<Action> actions = new HashSet<>();
      actions.add(Action.DEFAULT);
      when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
      when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
      when(requestContext.getMethod()).thenReturn("GET");
      mockUriInfo(PATH, uriInfo);
      when(requestContext.getHeaderString("X-Api-Key")).thenReturn("mock-api-key");
      when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
      when(harnessUserGroupService.isHarnessSupportEnabledForAccount(ACCOUNT_ID)).thenReturn(true);
      authRuleFilter.filter(requestContext);
    } catch (Exception e) {
    } finally {
      verify(auditServiceHelper, times(1))
          .reportForAuditingUsingAccountId(eq(ACCOUNT_ID), eq(null), any(), eq(Event.Type.INVOKED));
    }
  }

  @Test
  @Owner(developers = AKRITI)
  @Category(UnitTests.class)
  public void testAuditAPIKeyNotInvoked() {
    try {
      Set<Action> actions = new HashSet<>();
      actions.add(Action.DEFAULT);
      when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
      when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
      when(requestContext.getMethod()).thenReturn("GET");
      mockUriInfo(PATH, uriInfo);
      when(requestContext.getHeaderString("X-Api-Key")).thenReturn(null);
      when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
      when(harnessUserGroupService.isHarnessSupportEnabledForAccount(ACCOUNT_ID)).thenReturn(true);
      authRuleFilter.filter(requestContext);
    } catch (Exception e) {
    } finally {
      verify(auditServiceHelper, times(0))
          .reportForAuditingUsingAccountId(
              eq(ACCOUNT_ID), eq(null), any(ApiKeyEntry.class), eq(Event.Type.NON_WHITELISTED));
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFilterInAuthRuleFilterWithMultipleAuthRules() {
    Set<Action> actions = new HashSet<>();
    actions.add(Action.DEFAULT);
    when(resourceInfo.getResourceClass()).thenReturn(getResourceClassWithMultipleAnnotations());
    when(resourceInfo.getResourceMethod()).thenReturn(getResourceMethodWithMultipleAnnotations());
    when(requestContext.getMethod()).thenReturn("GET");
    mockUriInfo(PATH, uriInfo);
    when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    when(harnessUserGroupService.isHarnessSupportEnabled(ACCOUNT_ID, USER_ID)).thenReturn(true);
    when(whitelistService.isValidIPAddress(any(), any())).thenReturn(true);
    when(authService.getUserPermissionInfo(any(), any(), anyBoolean())).thenReturn(mockUserPermissionInfo());

    // test method with multiple annotations
    try {
      authRuleFilter.filter(requestContext);
    } catch (InvalidRequestException e) {
      fail("Exception was not expected in this test execution.");
    }

    ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(authHandler).authorizeAccountPermission(any(UserRequestContext.class), argumentCaptor.capture());

    List<PermissionAttribute> permissionAttributeList = argumentCaptor.getValue();
    assertThat(permissionAttributeList).hasSize(2);
    assertThat(permissionAttributeList.stream()
                   .map(permissionAttribute -> permissionAttribute.getPermissionType())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(Arrays.asList(ACCOUNT_MANAGEMENT, MANAGE_DELEGATES));

    // test method with no annotations. Class level annotations are expected
    when(resourceInfo.getResourceMethod()).thenReturn(getResourceMethodWithoutAnnotations());
    try {
      authRuleFilter.filter(requestContext);
    } catch (InvalidRequestException e) {
      fail("Exception was not expected in this test execution.");
    }

    argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(authHandler, times(2)).authorizeAccountPermission(any(UserRequestContext.class), argumentCaptor.capture());

    permissionAttributeList = argumentCaptor.getValue();
    assertThat(permissionAttributeList).hasSize(2);
    assertThat(permissionAttributeList.stream()
                   .map(permissionAttribute -> permissionAttribute.getPermissionType())
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrderElementsOf(Arrays.asList(LOGGED_IN, ACCOUNT_MANAGEMENT));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testHarnessUserGraphql() {
    testHarnessUserMethod("graphql", "POST", false);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testHarnessUserPOST() {
    testHarnessUserMethod("/api/services", "POST", true);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testHarnessUserPUT() {
    testHarnessUserMethod("/api/services", "PUT", true);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testHarnessUserDELETE() {
    testHarnessUserMethod("/api/services", "DELETE", true);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testHarnessUserGET() {
    testHarnessUserMethod("/api/services", "GET", false);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testFilter_For_NextGenRequest() {
    Class clazz = UserResourceNG.class;
    when(resourceInfo.getResourceClass()).thenReturn(clazz);
    when(resourceInfo.getResourceMethod()).thenReturn(getNgMockResourceMethod());
    boolean isNextGenRequest = authRuleFilter.isNextGenManagerRequest();
    assertThat(isNextGenRequest).isTrue();

    when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
        .thenReturn(AuthenticationFilter.NEXT_GEN_MANAGER_PREFIX);
    isNextGenRequest = authRuleFilter.isNextGenManagerRequest();
    assertThat(isNextGenRequest).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testWhitelistForGraphqlInAuthRuleFilter() {
    Set<Action> actions = new HashSet<>();
    actions.add(Action.DEFAULT);
    when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
    when(requestContext.getMethod()).thenReturn("GET");
    mockUriInfo(PATH, uriInfo);
    when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    when(harnessUserGroupService.isHarnessSupportEnabled(ACCOUNT_ID, USER_ID)).thenReturn(true);
    when(whitelistService.isValidIPAddress(any(), any())).thenReturn(true);
    when(whitelistService.checkIfFeatureIsEnabledAndWhitelisting(any(), any(), any(FeatureName.class)))
        .thenReturn(true);
    when(authService.getUserPermissionInfo(any(), any(), anyBoolean())).thenReturn(mockUserPermissionInfo());
    authRuleFilter.filter(requestContext);
    assertThat(requestContext.getMethod()).isEqualTo("GET");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testApiKeyAuthorizedAnnotation_withoutApiKey() {
    UserThreadLocal.set(null);
    Set<Action> actions = new HashSet<>();
    actions.add(Action.DEFAULT);
    when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getResourceMethodWithApiKeyAuthorizedAnnotation());
    when(requestContext.getMethod()).thenReturn("GET");
    mockUriInfo(PATH, uriInfo);
    when(whitelistService.checkIfFeatureIsEnabledAndWhitelisting(any(), any(), any(FeatureName.class)))
        .thenReturn(true);

    authRuleFilter.filter(requestContext);
    verify(requestContext, times(2)).getHeaderString(API_KEY_HEADER);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testApiKeyAuthorizedAnnotation_withApiKey() {
    UserThreadLocal.set(null);
    Set<Action> actions = new HashSet<>();
    actions.add(Action.DEFAULT);
    when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getResourceMethodWithApiKeyAuthorizedAnnotation());
    when(requestContext.getMethod()).thenReturn("GET");
    mockUriInfo(PATH, uriInfo);
    when(requestContext.getHeaderString(API_KEY_HEADER)).thenReturn(API_KEY);

    ApiKeyEntry apiKeyEntry = ApiKeyEntry.builder().build();
    UserPermissionInfo userPermissionInfo = mockUserPermissionInfo();
    UserRestrictionInfo userRestrictionInfo = UserRestrictionInfo.builder().build();
    when(apiKeyService.getByKey(API_KEY, ACCOUNT_ID)).thenReturn(apiKeyEntry);
    when(apiKeyService.getApiKeyPermissions(apiKeyEntry, ACCOUNT_ID)).thenReturn(userPermissionInfo);
    when(apiKeyService.getApiKeyRestrictions(apiKeyEntry, userPermissionInfo, ACCOUNT_ID))
        .thenReturn(userRestrictionInfo);

    when(whitelistService.isValidIPAddress(any(), any())).thenReturn(true);
    when(whitelistService.checkIfFeatureIsEnabledAndWhitelisting(any(), any(), any(FeatureName.class)))
        .thenReturn(true);

    authRuleFilter.filter(requestContext);
    assertThat(requestContext.getMethod()).isEqualTo("GET");
    verify(requestContext, times(4)).getHeaderString(API_KEY_HEADER);
    verify(whitelistService).checkIfFeatureIsEnabledAndWhitelisting(any(), any(), any(FeatureName.class));
    User user = UserThreadLocal.get();
    assertThat(user).isNotNull();
  }

  private void testHarnessUserMethod(String url, String method, boolean exception) {
    Set<Action> actions = new HashSet<>();
    actions.add(Action.READ);
    when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
    when(requestContext.getMethod()).thenReturn(method);
    mockUriInfo(url, uriInfo);
    when(harnessUserGroupService.isHarnessSupportUser(USER_ID)).thenReturn(true);
    when(harnessUserGroupService.isHarnessSupportEnabled(ACCOUNT_ID, USER_ID)).thenReturn(true);
    when(whitelistService.isValidIPAddress(any(), any())).thenReturn(true);
    when(whitelistService.checkIfFeatureIsEnabledAndWhitelisting(any(), any(), any(FeatureName.class)))
        .thenReturn(true);
    when(authService.getUserPermissionInfo(any(), any(), anyBoolean())).thenReturn(mockUserPermissionInfo());
    if (exception) {
      thrown.expect(AccessDeniedException.class);
    }
    authRuleFilter.filter(requestContext);

    if (!exception) {
      assertThat(requestContext.getMethod()).isEqualTo(method);
    }
  }

  private Class getMockResourceClass() {
    return AccountResource.class;
  }

  private Method getMockResourceMethod() {
    Class mockClass = AccountResource.class;
    try {
      return mockClass.getMethod("getAccount", String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Class getResourceClassWithMultipleAnnotations() {
    return DummyTestResource.class;
  }

  private Method getResourceMethodWithMultipleAnnotations() {
    Class mockClass = DummyTestResource.class;
    try {
      return mockClass.getMethod("testMultipleMethodAnnotations");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Method getResourceMethodWithoutAnnotations() {
    Class mockClass = DummyTestResource.class;
    try {
      return mockClass.getMethod("testMultipleClassAnnotations");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Method getResourceMethodWithApiKeyAuthorizedAnnotation() {
    Class mockClass = DummyTestResource.class;
    try {
      return mockClass.getMethod("testApiKeyAuthorizationAnnotation");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Method getNgMockResourceMethod() {
    Class mockClass = UserResourceNG.class;
    try {
      return mockClass.getMethod("getUser", String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private void mockUriInfo(String path, UriInfo uriInfo) {
    URI uri;
    try {
      uri = new URI(path);
    } catch (Exception e) {
      uri = null;
    }
    MultivaluedMap<String, String> parameters = mockParameters();
    when(uriInfo.getAbsolutePath()).thenReturn(uri);
    when(uriInfo.getPath()).thenReturn(path);
    when(uriInfo.getQueryParameters()).thenReturn(parameters);
    when(uriInfo.getPathParameters()).thenReturn(parameters);
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
  }

  private MultivaluedMap<String, String> mockParameters() {
    MultivaluedMap<String, String> mockMap = new MultivaluedHashMap<>();
    mockMap.add("accountId", ACCOUNT_ID);
    mockMap.add("appId", APP_ID);
    return mockMap;
  }

  private User mockUser(boolean harnessSupportUser) {
    User dummyUser = new User();
    Account dummyAccount = new Account();
    dummyAccount.setUuid(ACCOUNT_ID);
    dummyUser.setUuid(USER_ID);
    dummyUser.setName(USERNAME);
    dummyUser.setAccounts(Arrays.asList(dummyAccount));
    return dummyUser;
  }

  private UserPermissionInfo mockUserPermissionInfo() {
    Map<String, AppPermissionSummaryForUI> appPermissions = new HashMap<>();
    appPermissions.put(APP_ID, null);
    return UserPermissionInfo.builder().accountId(ACCOUNT_ID).appPermissionMap(appPermissions).build();
  }
}
