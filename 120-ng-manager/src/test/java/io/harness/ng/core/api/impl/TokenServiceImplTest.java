/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.ng.core.common.beans.ApiKeyType.SERVICE_ACCOUNT;
import static io.harness.ng.core.common.beans.ApiKeyType.USER;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.PIYUSH;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.NgManagerTestBase;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.mapper.TokenDTOMapper;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.ApiKeyTokenPasswordCacheHelper;
import io.harness.token.TokenValidationHelper;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
public class TokenServiceImplTest extends NgManagerTestBase {
  private TokenService tokenService;
  private TokenRepository tokenRepository;
  private ApiKeyService apiKeyService;
  private OutboxService outboxService;
  private ServiceAccountService serviceAccountService;
  private NgUserService ngUserService;
  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String identifier;
  private String parentIdentifier;
  private TokenDTO tokenDTO;
  private AccountOrgProjectValidator accountOrgProjectValidator;
  private TransactionTemplate transactionTemplate;
  private Token token;
  private AccountService accountService;
  private TokenValidationHelper tokenValidationHelper;
  @Inject private ApiKeyTokenPasswordCacheHelper apiKeyTokenPasswordCacheHelper;
  private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  Instant nextDay = Instant.now().plusSeconds(86400);
  String tokensUUId = generateUuid();

  @Before
  public void setup() throws IllegalAccessException {
    accountIdentifier = randomAlphabetic(10);
    orgIdentifier = randomAlphabetic(10);
    projectIdentifier = randomAlphabetic(10);
    identifier = randomAlphabetic(10);
    parentIdentifier = randomAlphabetic(10);
    tokenRepository = mock(TokenRepository.class);
    tokenService = new TokenServiceImpl();
    apiKeyService = mock(ApiKeyService.class);
    outboxService = mock(OutboxService.class);
    serviceAccountService = mock(ServiceAccountService.class);
    ngUserService = mock(NgUserService.class);
    accountOrgProjectValidator = mock(AccountOrgProjectValidator.class);
    transactionTemplate = mock(TransactionTemplate.class);
    accountService = mock(AccountService.class);
    tokenValidationHelper = new TokenValidationHelper();
    ngFeatureFlagHelperService = mock(NGFeatureFlagHelperService.class);
    apiKeyTokenPasswordCacheHelper = new ApiKeyTokenPasswordCacheHelper();

    tokenDTO = TokenDTO.builder()
                   .accountIdentifier(accountIdentifier)
                   .orgIdentifier(orgIdentifier)
                   .name(randomAlphabetic(10))
                   .projectIdentifier(projectIdentifier)
                   .identifier(identifier)
                   .parentIdentifier(parentIdentifier)
                   .apiKeyIdentifier(randomAlphabetic(10))
                   .apiKeyType(SERVICE_ACCOUNT)
                   .scheduledExpireTime(Instant.now().toEpochMilli())
                   .description("")
                   .tags(new HashMap<>())
                   .build();
    token = Token.builder()
                .scheduledExpireTime(Instant.now().plusSeconds(86500))
                .validTo(Instant.now().plusSeconds(86500))
                .validFrom(Instant.now())
                .accountIdentifier(accountIdentifier)
                .orgIdentifier(orgIdentifier)
                .name(randomAlphabetic(10))
                .projectIdentifier(projectIdentifier)
                .identifier(identifier)
                .parentIdentifier(parentIdentifier)
                .apiKeyIdentifier(randomAlphabetic(10))
                .apiKeyType(SERVICE_ACCOUNT)
                .description("")
                .tags(new ArrayList<>())
                .build();
    token.setUuid(tokensUUId);
    when(accountOrgProjectValidator.isPresent(any(), any(), any())).thenReturn(true);
    when(transactionTemplate.execute(any())).thenReturn(token);
    FieldUtils.writeField(tokenService, "tokenRepository", tokenRepository, true);
    FieldUtils.writeField(tokenService, "apiKeyService", apiKeyService, true);
    FieldUtils.writeField(tokenService, "serviceAccountService", serviceAccountService, true);
    FieldUtils.writeField(tokenService, "ngUserService", ngUserService, true);
    FieldUtils.writeField(tokenService, "outboxService", outboxService, true);
    FieldUtils.writeField(tokenService, "accountOrgProjectValidator", accountOrgProjectValidator, true);
    FieldUtils.writeField(tokenService, "transactionTemplate", transactionTemplate, true);
    FieldUtils.writeField(tokenService, "accountService", accountService, true);
    FieldUtils.writeField(tokenService, "tokenValidationHelper", tokenValidationHelper, true);
    FieldUtils.writeField(
        tokenValidationHelper, "apiKeyTokenPasswordCacheHelper", apiKeyTokenPasswordCacheHelper, true);
    FieldUtils.writeField(tokenService, "ngFeatureFlagHelperService", ngFeatureFlagHelperService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateToken_sat() {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(apiKey).when(apiKeyService).getApiKey(any(), any(), any(), any(), any(), any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    String tokenString = tokenService.createToken(tokenDTO);
    assertThat(tokenString).startsWith(SERVICE_ACCOUNT.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testCreateToken_noDescription() {
    TokenDTO dto = TokenDTO.builder()
                       .accountIdentifier(accountIdentifier)
                       .orgIdentifier(orgIdentifier)
                       .name(randomAlphabetic(10))
                       .projectIdentifier(projectIdentifier)
                       .identifier(identifier)
                       .parentIdentifier(parentIdentifier)
                       .apiKeyIdentifier(randomAlphabetic(10))
                       .apiKeyType(SERVICE_ACCOUNT)
                       .scheduledExpireTime(Instant.now().toEpochMilli())
                       .tags(new HashMap<>())
                       .build();
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(apiKey).when(apiKeyService).getApiKey(any(), any(), any(), any(), any(), any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(dto, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    String tokenString = tokenService.createToken(dto);
    assertThat(tokenString).isNotEmpty();
    assertThat(newToken.getDescription()).isNull();
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testInvalidToken_sat() {
    try {
      TokenDTO invalidExpiryTokenDTO = TokenDTO.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .name(randomAlphabetic(10))
                                           .projectIdentifier(projectIdentifier)
                                           .identifier(identifier)
                                           .parentIdentifier(parentIdentifier)
                                           .apiKeyIdentifier(randomAlphabetic(10))
                                           .apiKeyType(SERVICE_ACCOUNT)
                                           .validTo(Instant.now().toEpochMilli())
                                           .description("")
                                           .tags(new HashMap<>())
                                           .build();
      tokenService.createToken(invalidExpiryTokenDTO);
      failBecauseExceptionWasNotThrown(InvalidRequestException.class);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testGetToken_sat() {
    tokenDTO.setApiKeyType(SERVICE_ACCOUNT);
    token.setApiKeyType(SERVICE_ACCOUNT);
    String email = "ab17@goat.com";
    when(tokenRepository.findById(tokensUUId)).thenReturn(Optional.of(token));
    when(serviceAccountService.getServiceAccountDTO(
             accountIdentifier, orgIdentifier, projectIdentifier, parentIdentifier))
        .thenReturn(ServiceAccountDTO.builder().email(email).name(email).build());
    TokenDTO response = tokenService.getToken(tokensUUId, true);
    assertThat(response.getEmail()).isEqualTo(email);
    verify(serviceAccountService, times(1)).getServiceAccountDTO(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testRotateToken_sat() {
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(apiKey).when(apiKeyService).getApiKey(any(), any(), any(), any(), any(), any());
    doReturn(Optional.of(TokenDTOMapper.getTokenFromDTO(tokenDTO, Duration.ofDays(2).toMillis())))
        .when(tokenRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any(), any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    String tokenString =
        tokenService.rotateToken(accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT,
            parentIdentifier, tokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000));
    assertThat(tokenString).startsWith(SERVICE_ACCOUNT.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCreateToken_pat() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(apiKey).when(apiKeyService).getApiKey(any(), any(), any(), any(), any(), any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    String tokenString = tokenService.createToken(tokenDTO);
    assertThat(tokenString).startsWith(USER.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testRotateToken_pat() {
    tokenDTO.setApiKeyType(USER);
    token.setApiKeyType(USER);
    Principal principal = new UserPrincipal(tokenDTO.getParentIdentifier(), "", "", tokenDTO.getAccountIdentifier());
    SecurityContextBuilder.setContext(principal);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    ApiKey apiKey = ApiKey.builder().defaultTimeToExpireToken(Duration.ofDays(2).toMillis()).build();
    apiKey.setUuid(randomAlphabetic(10));
    doReturn(apiKey).when(apiKeyService).getApiKey(any(), any(), any(), any(), any(), any());
    doReturn(Optional.of(TokenDTOMapper.getTokenFromDTO(tokenDTO, Duration.ofDays(2).toMillis())))
        .when(tokenRepository)
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
            any(), any(), any(), any(), any(), any(), any());
    Token newToken = TokenDTOMapper.getTokenFromDTO(tokenDTO, Duration.ofDays(2).toMillis());
    newToken.setUuid(randomAlphabetic(10));
    doReturn(newToken).when(tokenRepository).save(any());
    AccountDTO accountDTO =
        AccountDTO.builder()
            .serviceAccountConfig(ServiceAccountConfig.builder().apiKeyLimit(5).tokenLimit(5).build())
            .build();
    doReturn(accountDTO).when(accountService).getAccount(any());
    String tokenString = tokenService.rotateToken(accountIdentifier, orgIdentifier, projectIdentifier, USER,
        parentIdentifier, tokenDTO.getApiKeyIdentifier(), identifier, Instant.now().plusMillis(1000));
    assertThat(tokenString).startsWith(USER.getValue());
    assertThat(tokenString.split("\\.")[1]).isEqualTo(token.getAccountIdentifier());
    assertThat(tokenString.split("\\.")[2]).isEqualTo(token.getUuid());
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateApiKeyToken_with_cache() {
    tokenDTO.setApiKeyType(SERVICE_ACCOUNT);
    token.setApiKeyType(SERVICE_ACCOUNT);
    String rawPassword = generateUuid();
    String encodedPassword = new BCryptPasswordEncoder($2A, 10).encode(rawPassword);
    String email = "test123@mailinator.in";
    token.setEncodedPassword(encodedPassword);
    when(tokenRepository.findById(anyString())).thenReturn(Optional.of(token));
    when(serviceAccountService.getServiceAccountDTO(
             accountIdentifier, orgIdentifier, projectIdentifier, parentIdentifier))
        .thenReturn(ServiceAccountDTO.builder().email(email).name(email).build());

    doReturn(false)
        .when(ngFeatureFlagHelperService)
        .isEnabled(accountIdentifier, FeatureName.PL_SUPPORT_JWT_TOKEN_SCIM_API);

    String delimiter = ".";
    final String apiKeyDummy = "sat" + delimiter + accountIdentifier + delimiter + identifier + delimiter + rawPassword;

    TokenDTO resultTokenDTO = tokenService.validateToken(accountIdentifier, apiKeyDummy);

    assertThat(resultTokenDTO).isNotNull();
    assertThat(resultTokenDTO.getEncodedPassword()).isNull();
    assertThat(resultTokenDTO.getEmail()).isEqualTo(email);
    assertThat(apiKeyTokenPasswordCacheHelper.get(identifier)).isEqualTo(rawPassword);
  }
}
