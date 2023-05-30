/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ADITYA;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.Role.Builder.aRole;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.cache.HarnessCacheManager;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.outbox.filter.OutboxEventFilter;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.DelegateTokenAuthenticator;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.WingsBaseTest;
import software.wings.app.PortalConfig;
import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.AuthToken;
import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.Role;
import software.wings.beans.RoleType;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.Workflow;
import software.wings.core.events.LoginEvent;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.GenericDbCache;
import software.wings.security.AppPermissionSummary;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserPermissionInfo;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.UserService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.inject.Inject;
import dev.morphia.AdvancedDatastore;
import io.serializer.HObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by anubhaw on 8/31/16.
 */
@OwnedBy(PL)
@TargetModule(HarnessModule._360_CG_MANAGER)
public class AuthServiceTest extends WingsBaseTest {
  private final String VALID_TOKEN = "VALID_TOKEN";
  private final String INVALID_TOKEN = "INVALID_TOKEN";
  private final String EXPIRED_TOKEN = "EXPIRED_TOKEN";
  private final String NOT_AVAILABLE_TOKEN = "NOT_AVAILABLE_TOKEN";
  private final String SMALL_TOKEN = "FOUR";
  private final String AUTH_SECRET = "AUTH_SECRET";

  @Mock private GenericDbCache cache;
  @Mock private Cache<String, User> userCache;
  @Mock private Cache<String, AuthToken> authTokenCache;
  @Mock private HPersistence persistence;
  @Mock private AdvancedDatastore advancedDatastore;
  @Mock private AccountService accountService;
  @Mock private SegmentHandler segmentHandler;
  @Mock FeatureFlagService featureFlagService;
  @Mock private ConfigurationController configurationController;
  @Mock private HarnessCacheManager harnessCacheManager;
  @Inject PortalConfig portalConfig;
  @Mock private DelegateTokenAuthenticator delegateTokenAuthenticator;
  @Inject @InjectMocks private UserService userService;
  @Inject @InjectMocks private AuthService authService;
  @Mock private UserMembershipClient userMembershipClient;
  @Inject private OutboxService outboxService;

  private Builder userBuilder = anUser().appId(APP_ID).email(USER_EMAIL).name(USER_NAME).password(PASSWORD);
  private String accountKey = "2f6b0988b6fb3370073c3d0505baee59";

  /**
   * Sets up.
   *
   * @throws Exception the exception
   */
  @Before
  public void setUp() throws Exception {
    initMocks(this);
    when(configurationController.isPrimary()).thenReturn(true);
    when(harnessCacheManager.getCache(anyString(), eq(String.class), eq(User.class), any())).thenReturn(userCache);
    when(userCache.get(USER_ID)).thenReturn(User.Builder.anUser().uuid(USER_ID).build());
    when(authTokenCache.get(VALID_TOKEN)).thenReturn(new AuthToken(ACCOUNT_ID, USER_ID, 86400000L));
    when(authTokenCache.get(EXPIRED_TOKEN)).thenReturn(new AuthToken(ACCOUNT_ID, USER_ID, 0L));
    when(cache.get(Application.class, APP_ID)).thenReturn(anApplication().uuid(APP_ID).appId(APP_ID).build());
    when(cache.get(Environment.class, ENV_ID))
        .thenReturn(anEnvironment().appId(APP_ID).uuid(ENV_ID).environmentType(EnvironmentType.NON_PROD).build());
    when(accountService.get(ACCOUNT_ID))
        .thenReturn(anAccount().withUuid(ACCOUNT_ID).withAccountKey(accountKey).build());
    when(cache.get(Account.class, ACCOUNT_ID))
        .thenReturn(anAccount().withUuid(ACCOUNT_ID).withAccountKey(accountKey).build());
    on(portalConfig).set("jwtAuthSecret", AUTH_SECRET);

    when(persistence.getDatastore(AuthToken.class)).thenReturn(advancedDatastore);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowExecutePermissionsForEnvs = new HashSet<>();
    workflowExecutePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowExecutePermissionsForEnvs(workflowExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), null);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC4_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowExecutePermissionsForEnvs = new HashSet<>();
    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowExecutePermissionsForEnvs(workflowExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC5_testCheckIfUserAllowedToDeployWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowExecutePermissionsForEnvs = new HashSet<>();
    workflowExecutePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowExecutePermissionsForEnvs(workflowExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> pipelineExecutePermissionsForEnvs = new HashSet<>();
    pipelineExecutePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineExecutePermissionsForEnvs(pipelineExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployPipelineToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployPipelineToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    UserThreadLocal.set(null);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployPipelineToEnv(application.getUuid(), null);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC4_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> pipelineExecutePermissionsForEnvs = new HashSet<>();
    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineExecutePermissionsForEnvs(pipelineExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC5_testCheckIfUserAllowedToDeployPipelineToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> pipelineExecutePermissionsForEnvs = new HashSet<>();
    pipelineExecutePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineExecutePermissionsForEnvs(pipelineExecutePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkIfUserAllowedToDeployWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isTrue();
  }

  /**
   * Test whether auth token is fetched from db if its not available in cache
   */
  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testAuthTokenNotAvailableInCache() {
    AuthToken authTokenInDB = new AuthToken(ACCOUNT_ID, USER_ID, 86400000L);
    when(advancedDatastore.get(AuthToken.class, NOT_AVAILABLE_TOKEN)).thenReturn(authTokenInDB);
    AuthToken authToken = authService.validateToken(NOT_AVAILABLE_TOKEN);
    assertThat(authToken).isNotNull().isInstanceOf(AuthToken.class);
    assertThat(authToken).isEqualTo(authTokenInDB);
    verify(advancedDatastore, times(1)).get(AuthToken.class, NOT_AVAILABLE_TOKEN);
  }

  /**
   * Should validate valid token.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldValidateValidToken() {
    AuthToken authToken = authService.validateToken(VALID_TOKEN);
    assertThat(authToken).isNotNull().isInstanceOf(AuthToken.class);
  }

  @Test
  @Owner(developers = ADITYA)
  @Category(UnitTests.class)
  public void shouldValidateSmallLengthToken() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream);
    System.setOut(printStream);
    AuthToken authTokenInDB = new AuthToken(ACCOUNT_ID, USER_ID, 86400000L);
    when(advancedDatastore.get(AuthToken.class, SMALL_TOKEN)).thenReturn(authTokenInDB);
    AuthToken authToken = authService.validateToken(SMALL_TOKEN);
    assertThat(authToken).isNotNull().isInstanceOf(AuthToken.class);
    assertThat(authToken).isEqualTo(authTokenInDB);
    String logMessage = outputStream.toString();
    String expectedMessage =
        String.format("Token with prefix %s not found in cache hence fetching it from db", SMALL_TOKEN);
    assertThat(logMessage).contains(expectedMessage);
    System.setOut(System.out);
    verify(advancedDatastore, times(1)).get(AuthToken.class, SMALL_TOKEN);
  }

  /**
   * Should throw invalid token exception for invalid token.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowInvalidTokenExceptionForInvalidToken() {
    assertThatThrownBy(() -> authService.validateToken(INVALID_TOKEN))
        .isInstanceOf(WingsException.class)
        .hasMessage(ErrorCode.INVALID_TOKEN.name());
  }

  /**
   * Should throw expired token exception for expired token.
   */
  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldThrowExpiredTokenExceptionForExpiredToken() {
    assertThatThrownBy(() -> authService.validateToken(EXPIRED_TOKEN))
        .isInstanceOf(WingsException.class)
        .hasMessage(ErrorCode.EXPIRED_TOKEN.name());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldAuthorizeWithAccountAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.ACCOUNT_ADMIN).build();
    User user = userBuilder.but().roles(asList(role)).build();
    String appId = null;
    try {
      authService.authorize(
          ACCOUNT_ID, appId, null, user, asList(new PermissionAttribute(ResourceType.USER, Action.READ)), null);
    } catch (Exception e) {
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDenyWithoutAccountAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.APPLICATION_ADMIN).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    String appId = null;
    assertThatThrownBy(()
                           -> authService.authorize(ACCOUNT_ID, appId, null, user,
                               asList(new PermissionAttribute(ResourceType.USER, Action.READ)), null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not authorized");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldAuthorizeWithAppAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.APPLICATION_ADMIN).withAppId(APP_ID).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    authService.authorize(
        ACCOUNT_ID, APP_ID, null, user, asList(new PermissionAttribute(ResourceType.ARTIFACT, Action.UPDATE)), null);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldAuthorizeReadWithEnvAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.NON_PROD_SUPPORT).withAppId(APP_ID).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    authService.authorize(
        ACCOUNT_ID, APP_ID, ENV_ID, user, asList(new PermissionAttribute(ResourceType.APPLICATION, Action.READ)), null);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDenyWithDiffAppAdminAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.APPLICATION_ADMIN).withAppId("APP_ID2").build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    assertThatThrownBy(()
                           -> authService.authorize(ACCOUNT_ID, APP_ID, null, user,
                               asList(new PermissionAttribute(ResourceType.APPLICATION, Action.READ)), null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not authorized");
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDenyWriteWithEnvAccess() {
    Role role = aRole().withAccountId(ACCOUNT_ID).withRoleType(RoleType.NON_PROD_SUPPORT).withAppId(APP_ID).build();
    role.onLoad();
    User user = userBuilder.but().roles(asList(role)).build();
    assertThatThrownBy(()
                           -> authService.authorize(ACCOUNT_ID, APP_ID, ENV_ID, user,
                               asList(new PermissionAttribute(ResourceType.APPLICATION, Action.UPDATE)), null))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessage("Not authorized");
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGenerateBearerTokenWithJWTToken() throws UnsupportedEncodingException {
    when(featureFlagService.isEnabled(ArgumentMatchers.any(FeatureName.class), anyString())).thenReturn(true);
    Account mockAccount =
        Account.Builder.anAccount().withUuid("kmpySmUISimoRrJL6NL73w").withAccountKey("TestAccount").build();
    User mockUser = getMockUser(mockAccount);
    mockUser.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    mockUser.setUuid("kmpySmUISimoRrJL6NL73w");
    when(userCache.get(USER_ID)).thenReturn(mockUser);
    User user = authService.generateBearerTokenForUser(mockUser);
    assertThat(user.getToken().length()).isGreaterThan(32);

    Algorithm algorithm = Algorithm.HMAC256(AUTH_SECRET);
    JWTVerifier verifier = JWT.require(algorithm).withIssuer("Harness Inc").build();
    String authTokenId = JWT.decode(user.getToken()).getClaim("authToken").asString();

    String tokenString = user.getToken();
    AuthToken authToken = new AuthToken(ACCOUNT_ID, USER_ID, 8640000L);
    authToken.setJwtToken(user.getToken());
    when(authTokenCache.get(authTokenId)).thenReturn(authToken);
    assertThat(authService.validateToken(tokenString)).isEqualTo(authToken);

    try {
      authService.validateToken(tokenString + "FakeToken");
      fail("WingsException should have been thrown");
    } catch (WingsException e) {
      assertThat(e.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIAL.name());
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testGenerateBearerTokenWithoutJWTToken() {
    when(featureFlagService.isEnabled(ArgumentMatchers.any(FeatureName.class), anyString())).thenReturn(false);
    Account mockAccount =
        Account.Builder.anAccount().withUuid("kmpySmUISimoRrJL6NL73w").withAccountKey("TestAccount").build();
    User mockUser = getMockUser(mockAccount);
    mockUser.setDefaultAccountId("kmpySmUISimoRrJL6NL73w");
    mockUser.setUuid("kmpySmUISimoRrJL6NL73w");
    when(userCache.get(USER_ID)).thenReturn(mockUser);
    User user = authService.generateBearerTokenForUser(mockUser);
    AuthToken authToken = new AuthToken(ACCOUNT_ID, USER_ID, 8640000L);
    JWT jwt = JWT.decode(user.getToken());
    String authTokenUuid = jwt.getClaim("authToken").asString();
    when(cache.get(any(), ArgumentMatchers.matches(authTokenUuid))).thenReturn(authToken);
    when(authTokenCache.get(authTokenUuid)).thenReturn(authToken);
    assertThat(user.getToken().length()).isGreaterThan(32);
    authService.validateToken(user.getToken());
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldSendSegmentTrackEvent() throws IllegalAccessException {
    when(featureFlagService.isEnabled(ArgumentMatchers.any(FeatureName.class), anyString())).thenReturn(false);
    Account mockAccount = Account.Builder.anAccount().withAccountKey("TestAccount").withUuid(ACCOUNT_ID).build();
    User mockUser = getMockUser(mockAccount);
    mockUser.setLastAccountId(ACCOUNT_ID);
    when(userCache.get(USER_ID)).thenReturn(mockUser);

    FieldUtils.writeField(authService, "segmentHandler", segmentHandler, true);
    authService.generateBearerTokenForUser(mockUser);
    try {
      Thread.sleep(10000);
      verify(segmentHandler, times(1))
          .reportTrackEvent(any(Account.class), anyString(), any(User.class), anyMap(), anyMap());
    } catch (InterruptedException | URISyntaxException e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  private User getMockUser(Account mockAccount) {
    return Builder.anUser()
        .uuid(USER_ID)
        .name("TestUser")
        .email("admin@abcd.io")
        .appId("TestApp")
        .accounts(Arrays.asList(mockAccount))
        .build();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void denyWorkflowCreationWhenEnvironmentAccessNotPresent() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    // Workflow with a different envId than what is allowed
    Workflow workflow = aWorkflow()
                            .appId(application.getUuid())
                            .envId(generateUuid())
                            .uuid(generateUuid())
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .build();

    Set<String> workflowCreatePermissionsForEnvs = new HashSet<>();
    workflowCreatePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().workflowCreatePermissionsForEnvs(workflowCreatePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();
    UserThreadLocal.set(user);

    assertThatThrownBy(() -> authService.checkWorkflowPermissionsForEnv(application.getUuid(), workflow, Action.CREATE))
        .isInstanceOf(WingsException.class)
        .hasMessageContaining("Access Denied");

    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createPipelineWhenEnvironmentAccessPresent() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();

    // empty pipeline should not throw error for invalid environment access
    Pipeline pipeline = Pipeline.builder().build();

    Set<String> pipelineCreatePermissionsForEnvs = new HashSet<>();
    pipelineCreatePermissionsForEnvs.add(generateUuid());

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder().pipelineCreatePermissionsForEnvs(pipelineCreatePermissionsForEnvs).build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;
    try {
      authService.checkPipelinePermissionsForEnv(application.getUuid(), pipeline, Action.CREATE);
    } catch (WingsException ex) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    // no error since the environment is not required in the pipeline
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void checkIfUserAllowedToRollbackWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    Set<String> workflowRollbackExecutePermissionsForEnvs = new HashSet<>();
    workflowRollbackExecutePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder()
            .rollbackWorkflowExecutePermissionsForEnvs(workflowRollbackExecutePermissionsForEnvs)
            .build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;

    try {
      authService.checkIfUserAllowedToRollbackWorkflowToEnv(application.getUuid(), envId);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void throwErrorIfUserNotAuthorizedToRollbackWorkflowToEnv() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();

    // No env where user is allowed to rollback workflow for
    Set<String> workflowRollbackExecutePermissionsForEnvs = new HashSet<>();

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder()
            .rollbackWorkflowExecutePermissionsForEnvs(workflowRollbackExecutePermissionsForEnvs)
            .build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);

    assertThatThrownBy(() -> authService.checkIfUserAllowedToRollbackWorkflowToEnv(application.getUuid(), envId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("User doesn't have rights to rollback Workflow in this Environment");

    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void allowWorkflowUpdateWhenEnvironmentAccessNotPresentAndWorkflowAccessExplicit() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();
    String workflowId = generateUuid();
    Workflow workflow = aWorkflow().uuid(workflowId).envId(generateUuid()).build();

    Set<String> workflowUpdatePermissionsForEnvs = new HashSet<>();
    Set<String> workflowUpdatePermissionsByEntity = new HashSet<>();
    workflowUpdatePermissionsByEntity.add(workflowId);
    workflowUpdatePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder()
            .workflowUpdatePermissionsForEnvs(workflowUpdatePermissionsForEnvs)
            .workflowUpdatePermissionsByEntity(workflowUpdatePermissionsByEntity)
            .build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;

    try {
      authService.checkWorkflowPermissionsForEnv(application.getUuid(), workflow, Action.UPDATE);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void allowPipelineCreationWhenEnvironmentAccessNotPresentAndPipelineAccessExplicit() {
    Application application = anApplication().name("appName").uuid(generateUuid()).build();
    String envId = generateUuid();
    String pipelineId = generateUuid();

    Pipeline pipeline = Pipeline.builder().uuid(pipelineId).build();

    Set<String> pipelineUpdatePermissionsForEnvs = new HashSet<>();
    Set<String> pipelineUpdatePermissionsByEntity = new HashSet<>();
    pipelineUpdatePermissionsByEntity.add(pipelineId);
    pipelineUpdatePermissionsForEnvs.add(envId);

    AppPermissionSummary appPermissionSummary =
        AppPermissionSummary.builder()
            .pipelineUpdatePermissionsForEnvs(pipelineUpdatePermissionsForEnvs)
            .pipelineUpdatePermissionsByEntity(pipelineUpdatePermissionsByEntity)
            .build();

    Map<String, AppPermissionSummary> appPermissionMapInternal = new HashMap<>();
    appPermissionMapInternal.put(application.getUuid(), appPermissionSummary);

    UserPermissionInfo userPermissionInfo =
        UserPermissionInfo.builder().appPermissionMapInternal(appPermissionMapInternal).build();
    UserRequestContext userRequestContext = UserRequestContext.builder().userPermissionInfo(userPermissionInfo).build();

    User user = anUser().uuid(generateUuid()).name("user-name").userRequestContext(userRequestContext).build();

    UserThreadLocal.set(user);
    boolean exceptionThrown = false;

    try {
      authService.checkPipelinePermissionsForEnv(application.getUuid(), pipeline, Action.CREATE);
    } catch (InvalidRequestException ue) {
      exceptionThrown = true;
    } finally {
      UserThreadLocal.unset();
    }
    assertThat(exceptionThrown).isFalse();
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testAuditLoginToNg() throws IOException {
    Account account = Account.Builder.anAccount()
                          .withAccountName(ACCOUNT_NAME)
                          .withCompanyName(COMPANY_NAME)
                          .withUuid(ACCOUNT_ID)
                          .withAppId(GLOBAL_APP_ID)
                          .build();
    User user = anUser().uuid(generateUuid()).accounts(Collections.singletonList(account)).name("user-name").build();
    UserThreadLocal.set(user);

    Call<ResponseDTO<Boolean>> call = Mockito.mock(Call.class);
    when(call.execute()).thenReturn(Response.success(ResponseDTO.newResponse(true)));
    when(userMembershipClient.isUserInScope(any(), any(), any(), any())).thenReturn(call);
    authService.auditLoginToNg(Collections.singletonList(account.getUuid()), user);

    List<OutboxEvent> outboxEvents = outboxService.list(OutboxEventFilter.builder().maximumEventsPolled(10).build());
    OutboxEvent outboxEvent = outboxEvents.get(outboxEvents.size() - 1);

    assertThat(outboxEvent.getEventType()).isEqualTo("Login");
    LoginEvent loginEvent =
        HObjectMapper.NG_DEFAULT_OBJECT_MAPPER.readValue(outboxEvent.getEventData(), LoginEvent.class);

    assertThat(loginEvent.getAccountIdentifier()).isEqualTo(account.getUuid());
    assertThat(loginEvent.getUserId()).isEqualTo(user.getUuid());
    assertThat(loginEvent.getUserName()).isEqualTo(user.getName());
    UserThreadLocal.unset();
  }
}
