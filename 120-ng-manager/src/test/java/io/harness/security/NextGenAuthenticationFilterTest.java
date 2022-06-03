/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.security.NextGenAuthenticationFilter.AUTHORIZATION_HEADER;
import static io.harness.security.NextGenAuthenticationFilter.X_API_KEY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.scim.resource.NGScimUserResource;
import io.harness.ng.serviceaccounts.resource.ServiceAccountResource;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Slf4j
@OwnedBy(PL)
@RunWith(PowerMockRunner.class)
@PrepareForTest({NGRestUtils.class})
public class NextGenAuthenticationFilterTest extends ApiKeyFilterTestBase {
  private static final String accountIdentifier = "accountIdentifier";
  private static final String incorrectAccountIdentifier = "incorrectAccountIdentifier";
  private TokenClient tokenClient;
  private ContainerRequestContext containerRequestContext;
  private String apiKey;
  private String newApiKey;
  private ResourceInfo resourceInfo = null;
  private NextGenAuthenticationFilter authenticationFilter;

  @Before
  public void setup() throws IllegalAccessException, NoSuchMethodException {
    MockitoAnnotations.initMocks(this);
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
    PowerMockito.mockStatic(NGRestUtils.class);
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
    PowerMockito.mockStatic(NGRestUtils.class);
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

    newApiKey = "sat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    PowerMockito.mockStatic(NGRestUtils.class);
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
    PowerMockito.mockStatic(NGRestUtils.class);
    TokenDTO tokenDTO = TokenDTO.builder()
                            .apiKeyType(ApiKeyType.SERVICE_ACCOUNT)
                            .encodedPassword(encodedPassword)
                            .valid(true)
                            .accountIdentifier(accountIdentifier)
                            .parentIdentifier(generateUuid())
                            .build();
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
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
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid accountId in token " + uuid)
        .hasMessageContaining(uuid);
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
    PowerMockito.mockStatic(NGRestUtils.class);
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
    PowerMockito.mockStatic(NGRestUtils.class);
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
    PowerMockito.mockStatic(NGRestUtils.class);
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
    apiKey = "pat" + delimiter + uuid;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Token length not matching for API token");

    // Invalid prefix for API token
    apiKey = "sat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(uuid);

    // Invalid prefix for new API token
    newApiKey = "sat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(uuid);

    // Raw password not matching for API token
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword + "1";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(uuid);

    // Raw password not matching for new API token
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword + "2";
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(uuid);

    // Invalid token
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    tokenDTO.setValid(false);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(uuid);

    // Invalid new API token
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    tokenDTO.setValid(false);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(uuid);

    // Token not found in DB
    apiKey = "pat" + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(apiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> null);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Token not found")
        .hasMessageContaining(uuid);

    // new API token not found in DB
    newApiKey = "pat" + delimiter + accountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> null);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Token not found")
        .hasMessageContaining(uuid);

    // new API token containing incorrect accountId
    newApiKey = "pat" + delimiter + incorrectAccountIdentifier + delimiter + uuid + delimiter + rawPassword;
    when(containerRequestContext.getHeaderString(X_API_KEY)).thenReturn(newApiKey);
    when(NGRestUtils.getResponse(any())).thenAnswer(invocationOnMock -> tokenDTO);
    assertThatThrownBy(() -> authenticationFilter.filter(containerRequestContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid accountId in token " + uuid)
        .hasMessageContaining(uuid);
  }
}
