/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.eraro.ErrorCode.DOMAIN_WHITELIST_FILTER_CHECK_FAILED;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.MEHUL;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;
import static software.wings.signup.BugsnagConstants.CLUSTER_TYPE;
import static software.wings.signup.BugsnagConstants.FREEMIUM;
import static software.wings.signup.BugsnagConstants.ONBOARDING;

import static java.util.Arrays.asList;
import static javax.ws.rs.client.Entity.form;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.recaptcha.ReCaptchaVerifier;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.common.beans.Generation;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.BugsnagTab;
import software.wings.beans.ErrorData;
import software.wings.beans.LoginRequest;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.exception.WingsExceptionMapper;
import software.wings.scheduler.AccountPasswordExpirationJob;
import software.wings.security.authentication.AuthenticationManager;
import software.wings.security.authentication.TwoFactorAuthenticationManager;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;
import software.wings.signup.BugsnagErrorReporter;
import software.wings.utils.AccountPermissionUtils;
import software.wings.utils.ResourceTestRule;

import com.google.inject.Inject;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.cache.Cache;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

/**
 * Created by peeyushaggarwal on 4/1/16.
 */

@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class UserResourceTest extends WingsBaseTest {
  public static final UserService USER_SERVICE = mock(UserService.class);
  public static final HarnessUserGroupService HARNESS_USER_GROUP_SERVICE = mock(HarnessUserGroupService.class);
  public static final UserGroupService USER_GROUP_SERVICE = mock(UserGroupService.class);
  public static final Map<String, Cache<?, ?>> CACHES = mock(Map.class);
  public static final AuthService AUTH_SERVICE = mock(AuthService.class);
  public static final AccountService ACCOUNT_SERVICE = mock(AccountService.class);
  public static final MainConfiguration MAIN_CONFIGURATION = mock(MainConfiguration.class);
  public static final AuthenticationManager AUTHENTICATION_MANAGER = mock(AuthenticationManager.class);
  public static final AccountPasswordExpirationJob ACCOUNT_PASSWORD_EXPIRATION_JOB =
      mock(AccountPasswordExpirationJob.class);
  public static final ReCaptchaVerifier RE_CAPTCHA_VERIFIER = mock(ReCaptchaVerifier.class);
  public static final FeatureFlagService FEATURE_FLAG_SERVICE = mock(FeatureFlagService.class);
  public static final TwoFactorAuthenticationManager TWO_FACTOR_AUTHENTICATION_MANAGER =
      mock(TwoFactorAuthenticationManager.class);
  static final AccountPermissionUtils ACCOUNT_PERMISSION_UTILS = mock(AccountPermissionUtils.class);
  private static final List<BugsnagTab> tab =
      Collections.singletonList(BugsnagTab.builder().tabName(CLUSTER_TYPE).key(FREEMIUM).value(ONBOARDING).build());
  private static final HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
  @Inject @InjectMocks private UserResource userResource;
  @Mock private BugsnagErrorReporter bugsnagErrorReporter;

  /**
   * The constant RESOURCES.
   */
  @ClassRule
  public static final ResourceTestRule RESOURCES =
      ResourceTestRule.builder()
          .instance(new UserResource(USER_SERVICE, AUTH_SERVICE, ACCOUNT_SERVICE, ACCOUNT_PERMISSION_UTILS,
              AUTHENTICATION_MANAGER, TWO_FACTOR_AUTHENTICATION_MANAGER, CACHES, HARNESS_USER_GROUP_SERVICE,
              USER_GROUP_SERVICE, MAIN_CONFIGURATION, ACCOUNT_PASSWORD_EXPIRATION_JOB, RE_CAPTCHA_VERIFIER,
              FEATURE_FLAG_SERVICE))
          .instance(new AbstractBinder() {
            @Override
            protected void configure() {
              bind(httpServletRequest).to(HttpServletRequest.class);
            }
          })
          .type(WingsExceptionMapper.class)
          .type(MultiPartFeature.class)
          .build();

  /**
   * Sets the up.
   *
   * @throws IOException Signals that an I/O exception has occurred.
   */
  @Before
  public void setUp() throws IOException {
    reset(USER_SERVICE);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldListUsers() {
    PageRequest pageRequest = mock(PageRequest.class);
    when(pageRequest.getOffset()).thenReturn("0");
    when(pageRequest.getPageSize()).thenReturn(30);
    when(USER_SERVICE.listUsers(any(), any(), any(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(aPageResponse().withResponse(asList(anUser().build())).build());
    userResource.list(pageRequest, UUIDGenerator.generateUuid(), null, false, false);
    verify(USER_SERVICE).getTotalUserCount(any(), anyBoolean());
  }

  @Test(expected = BadRequestException.class)
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldErrorOnListWhenAccountIdIsNotFound() {
    RestResponse<PageResponse<User>> restResponse =
        RESOURCES.client().target("/users").request().get(new GenericType<RestResponse<PageResponse<User>>>() {});
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void shouldLoginUserUsingPostRequest() {
    String basicAuthToken = UUIDGenerator.generateUuid();
    String username = "userEmail";
    String password = "userPassword";
    String actualString = username + ":" + password;
    String BasicBase64format = Base64.getEncoder().encodeToString(actualString.getBytes());
    String authorization = "Basic " + BasicBase64format;
    LoginRequest loginRequest = LoginRequest.builder().authorization(authorization).build();
    when(AUTHENTICATION_MANAGER.extractToken(loginRequest.getAuthorization(), "Basic")).thenReturn(basicAuthToken);
    when(AUTHENTICATION_MANAGER.defaultLoginAccount(basicAuthToken, null)).thenReturn(new User());
    userResource.login(loginRequest, null, null);
    verify(AUTHENTICATION_MANAGER, times(1)).defaultLoginAccount(basicAuthToken, null);
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldCheckInvite() {
    String accountId = UUIDGenerator.generateUuid();
    String inviteId = UUIDGenerator.generateUuid();
    userResource.checkInvite(accountId, inviteId, Generation.CG);
    verify(USER_SERVICE, times(1)).checkInviteStatus(any(), any());
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldCompleteInvite() {
    String accountId = UUIDGenerator.generateUuid();
    String inviteId = UUIDGenerator.generateUuid();
    UserInvite userInvite = anUserInvite().build();
    userResource.completeInvite(accountId, inviteId, userInvite);
    verify(USER_SERVICE, times(1)).completeInvite(any());
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldInviteUsers() {
    String accountId = UUIDGenerator.generateUuid();
    String inviteId = UUIDGenerator.generateUuid();
    UserInvite userInvite = anUserInvite().build();
    userResource.inviteUsers(accountId, userInvite);
    verify(USER_SERVICE, times(1)).inviteUsers(any());
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testAccountIDIsOptionalInSAMLLogin() throws URISyntaxException {
    String accountId = UUIDGenerator.generateUuid();
    String relayState = "test-relay-state";
    String samlResponse = "test-saml-response";
    MultivaluedMap<String, String> requestEntity = new MultivaluedHashMap<>();
    requestEntity.add("SAMLResponse", samlResponse);
    requestEntity.add("RelayState", relayState);
    List<MediaType> list = new ArrayList<>();
    list.add(MediaType.valueOf(MediaType.TEXT_HTML));
    list.add(MediaType.valueOf(MediaType.APPLICATION_JSON));
    when(httpServletRequest.getHeader(com.google.common.net.HttpHeaders.REFERER)).thenReturn("headervalue");
    when(AUTHENTICATION_MANAGER.samlLogin(eq("headervalue"), any(), any(), any()))
        .thenReturn(Response.status(200).build());
    Response responseWithAccountId = RESOURCES.client()
                                         .target("/users/saml-login")
                                         .queryParam("accountId", accountId)
                                         .request()
                                         .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                                         .post(form(requestEntity));

    assertThat(responseWithAccountId.getStatus()).isEqualTo(200);

    Response responseWithoutAccountId = RESOURCES.client()
                                            .target("/users/saml-login")
                                            .request()
                                            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                                            .post(form(requestEntity));
    assertThat(responseWithoutAccountId.getStatus()).isEqualTo(200);
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testSamlLogin_withDomainWhitelistException() throws URISyntaxException {
    String accountId = UUIDGenerator.generateUuid();
    String relayState = "test-relay-state";
    String samlResponse = "test-saml-response";
    MultivaluedMap<String, String> requestEntity = new MultivaluedHashMap<>();
    requestEntity.add("SAMLResponse", samlResponse);
    requestEntity.add("RelayState", relayState);

    when(httpServletRequest.getHeader(com.google.common.net.HttpHeaders.REFERER)).thenReturn("headervalue");
    doThrow(new WingsException(DOMAIN_WHITELIST_FILTER_CHECK_FAILED, USER))
        .when(AUTHENTICATION_MANAGER)
        .samlLogin(any());

    Response responseWithAccountId = RESOURCES.client()
                                         .target("/users/saml-login")
                                         .queryParam("accountId", accountId)
                                         .request()
                                         .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                                         .post(form(requestEntity));
    assertThat(responseWithAccountId.getStatus()).isEqualTo(401);
    assertThat(responseWithAccountId.getHeaders().get("X-HARNESS-ERROR"))
        .isEqualTo(List.of("DOMAIN_WHITELIST_FILTER_CHECK_FAILED"));

    Response responseWithoutAccountId = RESOURCES.client()
                                            .target("/users/saml-login")
                                            .request()
                                            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED)
                                            .post(form(requestEntity));
    assertThat(responseWithoutAccountId.getStatus()).isEqualTo(401);
    assertThat(responseWithoutAccountId.getHeaders().get("X-HARNESS-ERROR"))
        .isEqualTo(List.of("DOMAIN_WHITELIST_FILTER_CHECK_FAILED"));
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testBugsnagErrorReportingForTrialSignup() {
    UserInvite userInvite = anUserInvite().withEmail("invalid").build();
    try {
      userResource.trialSignup(userInvite);
    } catch (Exception e) {
      verify(bugsnagErrorReporter, times(1))
          .report(ErrorData.builder().exception(e).email("invalid").tabs(tab).build());
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = MEHUL)
  @Category(UnitTests.class)
  public void testBugsnagErrorReportingForResendVerificationEmail() {
    try {
      userResource.resendVerificationEmail("");
    } catch (Exception e) {
      verify(bugsnagErrorReporter, times(1)).report(ErrorData.builder().exception(e).email("").tabs(tab).build());
      assertThat(e).isNotNull();
    }
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testIsHarnessSupportUser() {
    String userId = UUIDGenerator.generateUuid();
    userResource.isHarnessSupportUser(userId);
    verify(HARNESS_USER_GROUP_SERVICE, times(1)).isHarnessSupportUser(userId);
  }
}
