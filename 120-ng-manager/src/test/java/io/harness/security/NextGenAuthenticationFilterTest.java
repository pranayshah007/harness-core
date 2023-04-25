/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.NGCommonEntityConstants.ACCOUNT_HEADER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.EXPIRED_TOKEN;
import static io.harness.eraro.ErrorCode.INVALID_TOKEN;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.security.NextGenAuthenticationFilter.AUTHORIZATION_HEADER;
import static io.harness.security.NextGenAuthenticationFilter.X_API_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.scim.resource.NGScimUserResource;
import io.harness.ng.serviceaccounts.resource.ServiceAccountResource;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.token.remote.TokenClient;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.util.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Slf4j
@OwnedBy(PL)
public class NextGenAuthenticationFilterTest extends ApiKeyFilterTestBase {
  public static final String API_TOKEN_DELIMITER = ".";
  private static final String accountIdentifier = "accountIdentifier";
  private static final String incorrectAccountIdentifier = "incorrectAccountIdentifier";
  private TokenClient tokenClient;
  private NGSettingsClient ngSettingsClient;
  private ContainerRequestContext containerRequestContext;
  private String apiKey;
  private String newApiKey;
  private ResourceInfo resourceInfo = null;
  private NextGenAuthenticationFilter authenticationFilter;
  private MockedStatic<NGRestUtils> ngRestUtilsMockedStatic;

  @Before
  public void setup() throws IllegalAccessException, NoSuchMethodException {
    MockitoAnnotations.initMocks(this);
    ngRestUtilsMockedStatic = mockStatic(NGRestUtils.class);
    Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate =
        new Predicate<Pair<ResourceInfo, ContainerRequestContext>>() {
          @Override
          public boolean test(Pair<ResourceInfo, ContainerRequestContext> resourceInfoContainerRequestContextPair) {
            return true;
          }
        };
    resourceInfo = mock(ResourceInfo.class);
    when(resourceInfo.getResourceClass()).thenReturn(getDefaultMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getDefaultMockResourceMethod());
    Map<String, String> serviceToSecretMapping = new HashMap<>();
    ngSettingsClient = Mockito.mock(NGSettingsClient.class);
    serviceToSecretMapping.put("Bearer", apiKey);
    authenticationFilter =
        Mockito.spy(new NextGenAuthenticationFilter(predicate, null, serviceToSecretMapping, tokenClient));
    authenticationFilter.setResourceInfo(resourceInfo);
    tokenClient = Mockito.mock(TokenClient.class);
    containerRequestContext = Mockito.mock(ContainerRequestContext.class);

    final UriInfo mockUriInfo = mock(UriInfo.class);
    doReturn(mockUriInfo).when(containerRequestContext).getUriInfo();
    MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put(NGCommonEntityConstants.ACCOUNT_KEY, Lists.newArrayList("accountIdentifier"));
    when(mockUriInfo.getQueryParameters()).thenReturn(queryParams);

    FieldUtils.writeField(authenticationFilter, "tokenClient", tokenClient, true);
  }

  @After
  public void cleanup() {
    ngRestUtilsMockedStatic.close();
  }

  private Class getDefaultMockResourceClass() {
    return ServiceAccountResource.class;
  }

  private Method getDefaultMockResourceMethod() {
    try {
      return getDefaultMockResourceClass().getMethod(
          "listServiceAccounts", String.class, String.class, String.class, List.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  private Class getMockResourceClass() {
    return NGScimUserResource.class;
  }

  private Method getMockResourceMethod() {
    try {
      return getMockResourceClass().getMethod("getUser", String.class, String.class);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void testFilter_scimAPI() {
    when(resourceInfo.getResourceClass()).thenReturn(getMockResourceClass());
    when(resourceInfo.getResourceMethod()).thenReturn(getMockResourceMethod());
    authenticationFilter.setResourceInfo(resourceInfo);
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    apiKey = "sat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(AUTHORIZATION_HEADER)).thenReturn("Bearer " + apiKey);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFilter_withoutApiKey() {
    when(containerRequestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(generateUuid());
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFilter_sat_valid() {
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    apiKey = "sat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                            .encodedPassword(encodedPassword)
                            .accountIdentifier(accountIdentifier)
                            .valid(true)
                            .parentIdentifier(generateUuid())
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    authenticationFilter.filter(containerRequestContext);
    Principal context = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(context).isNotNull();
    assertThat(context.getType()).isEqualByComparingTo(PrincipalType.SERVICE_ACCOUNT);
    assertThat(context.getName()).isEqualTo(tokenDTO.getParentIdentifier());
    assertThat(((ServiceAccountPrincipal) context).getAccountId()).isEqualTo(tokenDTO.getAccountIdentifier());

    newApiKey = "sat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);

    TokenDTO tokenDTO1 = TokenDTO.builder()
                             .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                             .encodedPassword(encodedPassword)
                             .accountIdentifier(accountIdentifier)
                             .valid(true)
                             .parentIdentifier(generateUuid())
                             .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO1);
    authenticationFilter.filter(containerRequestContext);
    Principal context1 = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(context1).isNotNull();
    assertThat(context1.getType()).isEqualByComparingTo(PrincipalType.SERVICE_ACCOUNT);
    assertThat(context1.getName()).isEqualTo(tokenDTO1.getParentIdentifier());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFilter_sat_invalid() {
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .build();
    when(NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock
            -> new InvalidRequestException("Invalid API Token: Token length not matching for API token"));
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Incorrect number of sections in api key
    apiKey = "sat" + delimiter + uuid;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // API key prefix not matching
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // new API key prefix not matching
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Token not matching
    apiKey = "sat" + delimiter + uuid + delimiter + rawPassword + "1";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // new API token not matching
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword + "2";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Invalid token
    apiKey = "sat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    tokenDTO.setValid(false);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Invalid new API token
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    tokenDTO.setValid(false);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // new API token containing incorrect accountId
    newApiKey = "sat" + delimiter + incorrectAccountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any()))
        .thenAnswer(invocationOnMock
            -> new InvalidRequestException("Invalid API Token: Token length not matching for API token"));
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAccountQueryParamForAPISpecFirst() {
    final UriInfo mockUriInfo = mock(UriInfo.class);
    doReturn(mockUriInfo).when(containerRequestContext).getUriInfo();
    MultivaluedMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put(NGCommonEntityConstants.ACCOUNT, Lists.newArrayList("account"));
    when(mockUriInfo.getQueryParameters()).thenReturn(queryParams);
    when(mockUriInfo.getPathParameters()).thenReturn(queryParams);

    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier("account")
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    authenticationFilter.filter(containerRequestContext);
    Principal context = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(context).isNotNull();
    assertThat(context.getType()).isEqualByComparingTo(PrincipalType.USER);
    assertThat(context.getName()).isEqualTo(tokenDTO.getParentIdentifier());
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAccountHeaderParamMismatchWithApiKeyAccountForAPISpecFirst() {
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    String otherAccount = "different-account-from-header";
    when(containerRequestContext.getHeaderString(ACCOUNT_HEADER)).thenReturn(otherAccount);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier("account")
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(tokenClient.validateApiKey(anyString(), any()))
        .thenAnswer(i -> new InvalidRequestException(String.format("Invalid API token %s: Token not found", uuid)));
    Throwable thrown =
        catchThrowableOfType(() -> authenticationFilter.filter(containerRequestContext), InvalidRequestException.class);

    assertThat(thrown).hasMessage(String.format("Error fetching ApiKey token details for account: %s", otherAccount));
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testAccountHeaderParamForAPISpecFirst() {
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(containerRequestContext.getHeaderString(ACCOUNT_HEADER)).thenReturn("account");
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier("account")
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    authenticationFilter.filter(containerRequestContext);
    Principal context = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(context).isNotNull();
    assertThat(context.getType()).isEqualByComparingTo(PrincipalType.USER);
    assertThat(context.getName()).isEqualTo(tokenDTO.getParentIdentifier());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testFilter_pat_valid() {
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    authenticationFilter.filter(containerRequestContext);
    Principal context = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(context).isNotNull();
    assertThat(context.getType()).isEqualByComparingTo(PrincipalType.USER);
    assertThat(context.getName()).isEqualTo(tokenDTO.getParentIdentifier());

    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);

    TokenDTO tokenDTO1 = TokenDTO.builder()
                             .apiKeyType(ApiKeyType.USER)
                             .encodedPassword(encodedPassword)
                             .valid(true)
                             .accountIdentifier(accountIdentifier)
                             .parentIdentifier(generateUuid())
                             .email("user@harness.io")
                             .username("user")
                             .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO1);
    authenticationFilter.filter(containerRequestContext);
    Principal context1 = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(context1).isNotNull();
    assertThat(context1.getType()).isEqualByComparingTo(PrincipalType.USER);
    assertThat(context1.getName()).isEqualTo(tokenDTO1.getParentIdentifier());
  }

  @Test
  @Owner(developers = {SOWMYA, SHASHANK})
  @Category(UnitTests.class)
  public void testFilter_pat_invalid() {
    String delimiter = ".";
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(tokenClient.validateApiKey(anyString(), any()))
        .thenAnswer(i -> new InvalidRequestException("Invalid API Token: Token length not matching for API token"));
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Token length not matching for API token
    apiKey = "pat" + delimiter + uuid;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Invalid prefix for API token
    apiKey = "sat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Invalid prefix for new API token
    newApiKey = "sat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Raw password not matching for API token
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword + "1";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Raw password not matching for new API token
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword + "2";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Invalid token
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    tokenDTO.setValid(false);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Invalid new API token
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    tokenDTO.setValid(false);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // Token not found in DB
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> null);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // new API token not found in DB
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> null);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);

    // new API token containing incorrect accountId
    newApiKey = "pat" + delimiter + incorrectAccountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testFilter_pat_invalid_for_error_codes() {
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Token length not matching for API token
    apiKey = "pat" + API_TOKEN_DELIMITER + uuid;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertErrorCode(INVALID_TOKEN);

    // new API token containing incorrect accountId
    newApiKey = "pat" + API_TOKEN_DELIMITER + incorrectAccountIdentifier + API_TOKEN_DELIMITER + uuid
        + API_TOKEN_DELIMITER + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    assertErrorCode(INVALID_TOKEN);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testFilter_pat_invalid_prefix_for_error_code() {
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Invalid prefix for API token
    apiKey = "sat" + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertErrorCode(INVALID_TOKEN);

    // Invalid prefix for new API token
    newApiKey = "sat" + API_TOKEN_DELIMITER + accountIdentifier + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER
        + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertErrorCode(INVALID_TOKEN);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testFilter_pat_wrong_password_for_error_code() {
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Raw password not matching for API token
    apiKey = "pat" + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER + rawPassword + "1";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertErrorCode(INVALID_TOKEN);

    // Raw password not matching for new API token
    newApiKey = "pat" + API_TOKEN_DELIMITER + accountIdentifier + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER
        + rawPassword + "2";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertErrorCode(INVALID_TOKEN);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testFilter_pat_expired_token_for_error_code() {
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Invalid token
    apiKey = "pat" + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    tokenDTO.setValid(false);
    assertErrorCode(EXPIRED_TOKEN);

    // Invalid new API token
    newApiKey = "pat" + API_TOKEN_DELIMITER + accountIdentifier + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER
        + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    tokenDTO.setValid(false);
    assertErrorCode(EXPIRED_TOKEN);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testFilter_pat_non_existing_token_for_error_code() {
    String uuid = generateUuid();
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);

    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.USER)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .email("user@harness.io")
                            .username("user")
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    when(authenticationFilter.testRequestPredicate(containerRequestContext)).thenReturn(true);

    // Token not found in DB
    apiKey = "pat" + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> null);
    assertErrorCode(INVALID_TOKEN);

    // new API token not found in DB
    newApiKey = "pat" + API_TOKEN_DELIMITER + accountIdentifier + API_TOKEN_DELIMITER + uuid + API_TOKEN_DELIMITER
        + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> null);
    assertErrorCode(INVALID_TOKEN);
  }

  private void assertErrorCode(ErrorCode errorCode) {
    try {
      authenticationFilter.filter(containerRequestContext);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(InvalidRequestException.class);
      InvalidRequestException invalidRequestException = (InvalidRequestException) exception;
      assertThat(invalidRequestException.getCode()).isEqualTo(errorCode);
    }
  }
}
