/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.INVALID_CREDENTIAL;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.JOHANNES;
import static io.harness.rule.OwnerRule.PHOENIKX;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.RUSHABH;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.SHUBHANSHU;
import static io.harness.rule.OwnerRule.VIKAS;

import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;
import static software.wings.security.AuthenticationFilter.NEXT_GEN_MANAGER_PREFIX;
import static software.wings.security.AuthenticationFilter.USER_IDENTITY_HEADER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.metrics.impl.ExternalApiMetricsServiceImpl;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateAuthService;

import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.AuthToken;
import software.wings.beans.User;
import software.wings.common.AuditHelper;
import software.wings.resources.AccountResource;
import software.wings.resources.ApiKeyResource;
import software.wings.resources.UserResourceNG;
import software.wings.resources.secretsmanagement.SecretsResourceNG;
import software.wings.service.intfc.ApiKeyService;
import software.wings.service.intfc.AuditService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.ExternalApiRateLimitingService;
import software.wings.service.intfc.HarnessApiKeyService;
import software.wings.service.intfc.UserService;
import software.wings.utils.DummyTestResource;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
public class AuthenticationFilterTest extends CategoryTest {
  private static final String FQDN = "agent.some-fqdn.harness.io";

  @Mock ResourceInfo resourceInfo = mock(ResourceInfo.class);
  @Mock MainConfiguration configuration = mock(MainConfiguration.class);
  @Mock AuthService authService = mock(AuthService.class);
  @Mock UserService userService = mock(UserService.class);
  @Mock AuditService auditService = mock(AuditService.class);
  @Mock AuditHelper auditHelper = mock(AuditHelper.class);
  @Mock ApiKeyService apiKeyService = mock(ApiKeyService.class);
  @Mock HarnessApiKeyService thirdPartyApiKeyService = mock(HarnessApiKeyService.class);
  @Mock ExternalApiRateLimitingService rateLimitingService = mock(ExternalApiRateLimitingService.class);
  @Mock SecretManager secretManager = mock(SecretManager.class);

  @Mock DelegateAuthService delegateAuthService = mock(DelegateAuthService.class);
  @Mock ExternalApiMetricsServiceImpl externalApiMetricsService = mock(ExternalApiMetricsServiceImpl.class);

  @InjectMocks AuthenticationFilter authenticationFilter;

  ContainerRequestContext context = mock(ContainerRequestContext.class);

  SecurityContext securityContext = mock(SecurityContext.class);

  @Before
  public void setUp() {
    authenticationFilter = new AuthenticationFilter(userService, authService, auditService, auditHelper, apiKeyService,
        thirdPartyApiKeyService, rateLimitingService, secretManager, delegateAuthService, externalApiMetricsService);
    authenticationFilter = spy(authenticationFilter);
    when(context.getSecurityContext()).thenReturn(securityContext);
    when(securityContext.isSecure()).thenReturn(true);
    PortalConfig portalConfig = mock(PortalConfig.class);
    when(configuration.getPortal()).thenReturn(portalConfig);
    doNothing().when(externalApiMetricsService).recordApiRequestMetric(any(), any(), any());
    doReturn(false).when(authenticationFilter).isScimAPI();
    doReturn(false).when(authenticationFilter).isApiKeyAuthorizationAPI();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testAuthenticationFilterTestOptions() throws IOException {
    when(context.getMethod()).thenReturn(HttpMethod.OPTIONS);
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();

    doReturn(true).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testNoAuthorizationToken() throws IOException {
    try {
      doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
      doReturn(false).when(authenticationFilter).externalFacingAPI();
      doReturn(false).when(authenticationFilter).delegateAPI();
      doReturn(false).when(authenticationFilter).delegateAuth2API();
      doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
      doReturn(false).when(authenticationFilter).isAdminPortalRequest();

      //      doReturn(false).when(authenticationFilter).thirdPartyApi();
      authenticationFilter.filter(context);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThatExceptionOfType(WingsException.class);
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testDelegateRequestAuthentication() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Delegate token");
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    //    doReturn(false).when(authenticationFilter).thirdPartyApi();
    doReturn(true).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).identityServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(context.getUriInfo()).thenReturn(uriInfo);
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void testDelegateRequestAuthenticationWithMtls() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Delegate token");
    when(context.getHeaderString(HEADER_AGENT_MTLS_AUTHORITY)).thenReturn(FQDN);
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(true).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).identityServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("accountId", Arrays.asList(ACCOUNT_ID));
    when(uriInfo.getPathParameters()).thenReturn(queryParams);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(context.getUriInfo()).thenReturn(uriInfo);
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();

    verify(delegateAuthService, times(1)).validateDelegateToken(any(), any(), any(), any(), any(), eq(true));
    verify(delegateAuthService, times(1))
        .validateDelegateToken(eq(ACCOUNT_ID), any(), any(), any(), eq(FQDN), eq(true));
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testLearningEngineRequestAuthentication() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("LearningEngine token");
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(true).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).identityServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testIdentityServiceRequestAuthentication() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("IdentityService token");
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(false).when(authenticationFilter).isNextGenManagerRequest(any());
    doReturn(true).when(authenticationFilter).identityServiceAPI();
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void adminPortalRequestAuthentication() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("AdminPortal token");
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).isScimAPI();
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(true).when(authenticationFilter).isAdminPortalRequest();
    authenticationFilter.filter(context);
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testRequestAuthenticatedByIdentitySvc() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("IdentityService token");
    when(context.getHeaderString(USER_IDENTITY_HEADER)).thenReturn("userId");
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).identityServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(false).when(authenticationFilter).isNextGenManagerRequest(any());
    doReturn(true).when(authenticationFilter).isAuthenticatedByIdentitySvc(any(ContainerRequestContext.class));
    doReturn(true).when(authenticationFilter).isIdentityServiceOriginatedRequest(any(ContainerRequestContext.class));
    User user = mock(User.class);
    doReturn(user).when(userService).getUserFromCacheOrDB("userId");
    authenticationFilter.filter(context);
    assertThat(UserThreadLocal.get()).isNotNull();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExternalApiAuthentication() throws IOException {
    String apiKey = "ApiKey";
    when(context.getHeaderString(API_KEY_HEADER)).thenReturn(apiKey);
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(true).when(authenticationFilter).externalFacingAPI();
    doReturn(false).when(rateLimitingService).rateLimitRequest(anyString());
    doReturn(true).when(apiKeyService).validate(apiKey, ACCOUNT_ID);
    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("accountId", Arrays.asList(ACCOUNT_ID));
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
    when(uriInfo.getAbsolutePath()).thenReturn(URI.create("/abc/def"));
    when(context.getUriInfo()).thenReturn(uriInfo);
    authenticationFilter.filter(context);
    verify(apiKeyService).validate(eq(apiKey), eq(ACCOUNT_ID));
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExternalApiRateLimiting() throws IOException {
    String apiKey = "ApiKey";
    when(context.getHeaderString(API_KEY_HEADER)).thenReturn(apiKey);
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).identityServiceAPI();
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(true).when(authenticationFilter).externalFacingAPI();
    doReturn(true).when(rateLimitingService).rateLimitRequest(anyString());
    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("accountId", Arrays.asList(ACCOUNT_ID));
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
    when(uriInfo.getAbsolutePath()).thenReturn(URI.create("/abc/def"));
    when(context.getUriInfo()).thenReturn(uriInfo);
    assertThatThrownBy(() -> authenticationFilter.filter(context)).isInstanceOf(WebApplicationException.class);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testApiKeyAuthorizedAuthentication() throws IOException {
    String apiKey = "ApiKey";
    when(context.getHeaderString(API_KEY_HEADER)).thenReturn(apiKey);
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).delegateAuth2API();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(false).when(rateLimitingService).rateLimitRequest(anyString());
    doReturn(true).when(authenticationFilter).isApiKeyAuthorizationAPI();
    doReturn(true).when(apiKeyService).validate(apiKey, ACCOUNT_ID);

    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("accountId", Arrays.asList(ACCOUNT_ID));
    when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
    when(uriInfo.getAbsolutePath()).thenReturn(URI.create("/abc/def"));
    when(context.getUriInfo()).thenReturn(uriInfo);

    authenticationFilter.filter(context);
    verify(apiKeyService).validate(eq(apiKey), eq(ACCOUNT_ID));
    assertThat(context.getSecurityContext().isSecure()).isTrue();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testApiKeyAuthorizedAuthenticationWithoutApiKey() {
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(false).when(rateLimitingService).rateLimitRequest(anyString());
    doReturn(true).when(authenticationFilter).isApiKeyAuthorizationAPI();

    on(authenticationFilter).set("resourceInfo", resourceInfo);
    when(resourceInfo.getResourceMethod()).thenReturn(getCgMockResourceMethod());
    when(resourceInfo.getResourceClass()).thenReturn(getCgMockResourceClass());

    assertThatThrownBy(() -> authenticationFilter.filter(context)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testApiKeyAuthorizedAuthenticationWithoutApiKeyAndAllowEmptyApiKey() throws IOException {
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doReturn(false).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
    doReturn(false).when(authenticationFilter).isAdminPortalRequest();
    doReturn(false).when(authenticationFilter).externalFacingAPI();
    doReturn(false).when(rateLimitingService).rateLimitRequest(anyString());
    doReturn(true).when(authenticationFilter).isApiKeyAuthorizationAPI();

    on(authenticationFilter).set("resourceInfo", resourceInfo);
    when(resourceInfo.getResourceMethod()).thenReturn(getResourceMethodWithAllowEmptyApiKey());
    when(resourceInfo.getResourceClass()).thenReturn(getResourceClassWithAllowEmptyApiKey());

    authenticationFilter.filter(context);
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testInvalidBearerTokenPresent() throws IOException {
    try {
      when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bearerToken");
      doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
      doReturn(false).when(authenticationFilter).delegateAPI();
      doReturn(false).when(authenticationFilter).delegateAuth2API();
      doReturn(false).when(authenticationFilter).externalFacingAPI();
      doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
      doReturn(false).when(authenticationFilter).identityServiceAPI();
      doReturn(false).when(authenticationFilter).isAdminPortalRequest();
      doReturn(false).when(authenticationFilter).isNextGenManagerRequest(any());
      doReturn(false).when(authenticationFilter).isInternalRequest(any());
      when(authService.validateToken(anyString())).thenThrow(new WingsException(ErrorCode.USER_DOES_NOT_EXIST));
      authenticationFilter.filter(context);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException e) {
      assertThatExceptionOfType(WingsException.class);
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testValidBearerTokenPresent() throws IOException {
    try {
      when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bearerToken");
      doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
      doReturn(false).when(authenticationFilter).delegateAPI();
      doReturn(false).when(authenticationFilter).delegateAuth2API();
      doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
      doReturn(false).when(authenticationFilter).externalFacingAPI();
      doReturn(false).when(authenticationFilter).identityServiceAPI();
      doReturn(false).when(authenticationFilter).isAdminPortalRequest();
      doReturn(false).when(authenticationFilter).isNextGenManagerRequest(any());
      doReturn(false).when(authenticationFilter).isInternalRequest(any());
      AuthToken authToken = new AuthToken(ACCOUNT_ID, "testUser", 0L);
      authToken.setUser(mock(User.class));
      when(authService.validateToken(anyString())).thenReturn(authToken);
      authenticationFilter.filter(context);
    } catch (WingsException e) {
      fail(e.getMessage(), WingsException.class);
    }
  }

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testIncorrectToken() throws IOException {
    try {
      when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("fakeToken");
      doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
      doReturn(false).when(authenticationFilter).delegateAPI();
      doReturn(false).when(authenticationFilter).delegateAuth2API();
      doReturn(false).when(authenticationFilter).learningEngineServiceAPI();
      doReturn(false).when(authenticationFilter).externalFacingAPI();
      doReturn(false).when(authenticationFilter).isAdminPortalRequest();
      doReturn(false).when(authenticationFilter).isNextGenManagerRequest(any());
      doReturn(false).when(authenticationFilter).identityServiceAPI();
      doReturn(false).when(authenticationFilter).isInternalRequest(any());
      authenticationFilter.filter(context);
    } catch (WingsException e) {
      assertThatExceptionOfType(WingsException.class);
    }
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testIsNextGenAuthorizationValid_For_NextGenAuthorization() {
    when(secretManager.verifyJWTToken(anyString(), eq(JWT_CATEGORY.NEXT_GEN_MANAGER_SECRET)))
        .thenReturn(new HashMap<>());
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(NEXT_GEN_MANAGER_PREFIX);
    boolean isAuthorizationValid = authenticationFilter.isNextGenAuthorizationValid(context);
    assertThat(isAuthorizationValid).isTrue();

    when(secretManager.verifyJWTToken(anyString(), eq(JWT_CATEGORY.NEXT_GEN_MANAGER_SECRET)))
        .thenThrow(new WingsException(INVALID_CREDENTIAL));
    try {
      isAuthorizationValid = authenticationFilter.isNextGenAuthorizationValid(context);
    } catch (WingsException ex) {
      isAuthorizationValid = false;
      assertThat(ex.getCode()).isEqualTo(INVALID_CREDENTIAL);
    }
    assertThat(isAuthorizationValid).isFalse();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testIsNextGenManagerRequest_For_NextGenAuthorization() {
    Class clazz = SecretsResourceNG.class;
    when(resourceInfo.getResourceClass()).thenReturn(clazz);
    when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());

    boolean isAuthorizationValid = authenticationFilter.isNextGenManagerRequest(resourceInfo);
    assertThat(isAuthorizationValid).isTrue();

    clazz = AccountResource.class;
    when(resourceInfo.getResourceClass()).thenReturn(clazz);

    isAuthorizationValid = authenticationFilter.isNextGenManagerRequest(resourceInfo);
    assertThat(isAuthorizationValid).isFalse();
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testIsInternalRequest_For_NextGenAuthorization() {
    Class clazz = ApiKeyResource.class;
    when(resourceInfo.getResourceClass()).thenReturn(clazz);
    when(resourceInfo.getResourceMethod()).thenReturn(getInternalRequestMockResourceMethod());

    boolean isAuthorizationValid = authenticationFilter.isInternalRequest(resourceInfo);
    assertThat(isAuthorizationValid).isTrue();

    when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());

    isAuthorizationValid = authenticationFilter.isInternalRequest(resourceInfo);
    assertThat(isAuthorizationValid).isFalse();
  }

  private Method getMockResourceMethod() {
    Class mockClass = UserResourceNG.class;
    try {
      return mockClass.getMethod("getUser", String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Class getCgMockResourceClass() {
    return AccountResource.class;
  }

  private Method getCgMockResourceMethod() {
    Class mockClass = AccountResource.class;
    try {
      return mockClass.getMethod("getAccount", String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Class getResourceClassWithAllowEmptyApiKey() {
    return DummyTestResource.class;
  }

  private Method getResourceMethodWithAllowEmptyApiKey() {
    Class mockClass = DummyTestResource.class;
    try {
      return mockClass.getMethod("testApiKeyAuthorizationAnnotationWithAllowEmptyApiKey");
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Method getInternalRequestMockResourceMethod() {
    Class mockClass = ApiKeyResource.class;
    try {
      return mockClass.getMethod("validate", String.class, String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }
}
