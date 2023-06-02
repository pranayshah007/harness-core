/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.core.account.ServiceAccountConfig.DEFAULT_TOKEN_LIMIT;
import static io.harness.ng.core.entities.ApiKey.DEFAULT_TTL_FOR_TOKEN;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.api.utils.JWTTokenFlowAuthFilterUtils;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.Token;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.ng.core.events.TokenCreateEvent;
import io.harness.ng.core.events.TokenDeleteEvent;
import io.harness.ng.core.events.TokenUpdateEvent;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.mapper.TokenDTOMapper;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.TokenRepository;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.token.TokenValidationHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class TokenServiceImpl implements TokenService {
  @Inject private TokenRepository tokenRepository;
  @Inject private ApiKeyService apiKeyService;
  @Inject private ServiceAccountService serviceAccountService;
  @Inject private OutboxService outboxService;
  @Inject private AccountOrgProjectValidator accountOrgProjectValidator;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private NgUserService ngUserService;
  @Inject private AccountService accountService;
  @Inject private TokenValidationHelper tokenValidationHelper;
  @Inject private JWTTokenFlowAuthFilterUtils jwtTokenAuthFilterHelper;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private static final String deliminator = ".";

  @Override
  public String createToken(TokenDTO tokenDTO) {
    validateTokenRequest(tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(), tokenDTO.getProjectIdentifier(),
        tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier(), tokenDTO);
    validateTokenLimit(tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(), tokenDTO.getProjectIdentifier(),
        tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier());
    String randomString = RandomStringUtils.random(20, 0, 0, true, true, null, new SecureRandom());
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder($2A, 10);
    String tokenString = passwordEncoder.encode(randomString);
    Optional<ApiKey> apiKeyOptional = apiKeyService.getApiKey(tokenDTO.getAccountIdentifier(),
        tokenDTO.getOrgIdentifier(), tokenDTO.getProjectIdentifier(), tokenDTO.getApiKeyType(),
        tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier());
    if (apiKeyOptional.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Api key not present in scope for identifier: [%s]", tokenDTO.getApiKeyIdentifier()));
    }

    try {
      Token token = TokenDTOMapper.getTokenFromDTO(tokenDTO, apiKeyOptional.get().getDefaultTimeToExpireToken());
      token.setEncodedPassword(tokenString);
      validate(token);
      Token newToken = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Token savedToken = tokenRepository.save(token);
        outboxService.save(new TokenCreateEvent(TokenDTOMapper.getDTOFromToken(savedToken)));
        return savedToken;
      }));
      return token.getApiKeyType().getValue() + deliminator + newToken.getAccountIdentifier() + deliminator
          + newToken.getUuid() + deliminator + randomString;
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("Try using different token name, [%s] already exists", tokenDTO.getIdentifier()));
    }
  }

  @VisibleForTesting
  protected void validateTokenRequest(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, TokenDTO tokenDTO) {
    if (!accountOrgProjectValidator.isPresent(accountIdentifier, orgIdentifier, projectIdentifier)) {
      throw new InvalidArgumentsException(String.format("Project [%s] in Org [%s] and Account [%s] does not exist",
                                              accountIdentifier, orgIdentifier, projectIdentifier),
          USER_SRE);
    }
    validateTokenExpiryTime(tokenDTO);
    Optional<ApiKey> apiKeyOptional = apiKeyService.getApiKey(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier);
    if (apiKeyOptional.isEmpty()) {
      createApiKey(tokenDTO);
    }
  }

  private void createApiKey(TokenDTO tokenDTO) {
    apiKeyService.createApiKey(ApiKeyDTO.builder()
                                   .name(tokenDTO.getApiKeyIdentifier().concat("_auto"))
                                   .description("Auto Generated API key")
                                   .accountIdentifier(tokenDTO.getAccountIdentifier())
                                   .orgIdentifier(tokenDTO.getOrgIdentifier())
                                   .projectIdentifier(tokenDTO.getProjectIdentifier())
                                   .identifier(tokenDTO.getApiKeyIdentifier())
                                   .parentIdentifier(tokenDTO.getParentIdentifier())
                                   .apiKeyType(tokenDTO.getApiKeyType())
                                   .defaultTimeToExpireToken(DEFAULT_TTL_FOR_TOKEN)
                                   .build());
  }

  private void validateTokenExpiryTime(TokenDTO tokenDTO) {
    if (tokenDTO.getValidTo() != null && (tokenDTO.getValidTo() < Instant.now().toEpochMilli())) {
      throw new InvalidRequestException(
          String.format("Token's validTo cannot be set before current time TokenDTO: [%s]", tokenDTO), USER_SRE);
    }

    if (tokenDTO.getValidTo() != null && tokenDTO.getValidFrom() != null
        && tokenDTO.getValidFrom() > tokenDTO.getValidTo()) {
      throw new InvalidRequestException(
          String.format("Token's validFrom time cannot be after validTo time TokenDTO: [%s]", tokenDTO), USER_SRE);
    }
  }

  private void validateTokenLimit(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier) {
    ServiceAccountConfig serviceAccountConfig = accountService.getAccount(accountIdentifier).getServiceAccountConfig();
    long tokenLimit = serviceAccountConfig != null ? serviceAccountConfig.getTokenLimit() : DEFAULT_TOKEN_LIMIT;
    long existingTokenCount =
        tokenRepository
            .countByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier);
    if (existingTokenCount >= tokenLimit) {
      throw new InvalidRequestException("Maximum limit has reached");
    }
  }

  private void validateUpdateTokenRequest(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, TokenDTO tokenDTO) {
    if (tokenDTO.getScheduledExpireTime() != null) {
      throw new InvalidRequestException("Rotated tokens cannot be updated", USER_SRE);
    }
    validateTokenRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier, tokenDTO);
  }

  @Override
  public boolean revokeToken(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, String identifier) {
    Optional<Token> optionalToken =
        tokenRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier,
                identifier);
    Preconditions.checkState(optionalToken.isPresent(), "No token present with identifier: " + identifier);
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      tokenRepository.deleteById(optionalToken.get().getUuid());
      tokenValidationHelper.invalidateApiKeyToken(optionalToken.get().getUuid());
      outboxService.save(new TokenDeleteEvent(TokenDTOMapper.getDTOFromToken(optionalToken.get())));
      return true;
    }));
  }

  @Override
  public TokenDTO getToken(String tokenId, boolean withEncodedPassword) {
    Optional<Token> optionalToken = tokenRepository.findById(tokenId);
    if (optionalToken.isPresent()) {
      TokenDTO tokenDTO = optionalToken.map(TokenDTOMapper::getDTOFromToken).orElse(null);
      if (withEncodedPassword) {
        tokenDTO.setEncodedPassword(optionalToken.get().getEncodedPassword());
      }
      if (ApiKeyType.USER == tokenDTO.getApiKeyType()) {
        Optional<UserInfo> optionalUserInfo = ngUserService.getUserById(tokenDTO.getParentIdentifier());
        if (optionalUserInfo.isPresent()) {
          UserInfo userInfo = optionalUserInfo.get();
          tokenDTO.setEmail(userInfo.getEmail());
          tokenDTO.setUsername(userInfo.getName());
          return tokenDTO;
        }
      } else {
        ServiceAccountDTO serviceAccountDTO =
            serviceAccountService.getServiceAccountDTO(tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(),
                tokenDTO.getProjectIdentifier(), tokenDTO.getParentIdentifier());
        tokenDTO.setEmail(serviceAccountDTO.getEmail());
        tokenDTO.setUsername(serviceAccountDTO.getName());
        return tokenDTO;
      }
    }
    return null;
  }

  @Override
  public String rotateToken(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier, String identifier,
      Instant scheduledExpireTime) {
    Optional<Token> optionalToken =
        tokenRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier,
                identifier);
    Preconditions.checkState(optionalToken.isPresent(), "No token present with identifier: " + identifier);
    Token tokenThatNeedsToBeRotated = optionalToken.get();
    TokenDTO oldTokenDTO = TokenDTOMapper.getDTOFromToken(tokenThatNeedsToBeRotated);
    String oldIdentifier = tokenThatNeedsToBeRotated.getIdentifier();

    tokenThatNeedsToBeRotated.setIdentifier("rotated_" + RandomStringUtils.randomAlphabetic(15));
    tokenThatNeedsToBeRotated.setScheduledExpireTime(scheduledExpireTime);
    tokenThatNeedsToBeRotated.setValidUntil(new Date(tokenThatNeedsToBeRotated.getExpiryTimestamp().toEpochMilli()));

    Token newToken = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Token savedRotatedToken = tokenRepository.save(tokenThatNeedsToBeRotated);
      TokenDTO newTokenDTO = TokenDTOMapper.getDTOFromToken(savedRotatedToken);
      outboxService.save(new TokenUpdateEvent(oldTokenDTO, newTokenDTO));
      return savedRotatedToken;
    }));

    TokenDTO rotatedTokenDTO = TokenDTOMapper.getDTOFromTokenForRotation(newToken);
    rotatedTokenDTO.setIdentifier(oldIdentifier);
    return createToken(rotatedTokenDTO);
  }

  @Override
  public TokenDTO updateToken(TokenDTO tokenDTO) {
    validateUpdateTokenRequest(tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(),
        tokenDTO.getProjectIdentifier(), tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(),
        tokenDTO.getApiKeyIdentifier(), tokenDTO);
    Optional<Token> optionalToken =
        tokenRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifierAndIdentifier(
                tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(), tokenDTO.getProjectIdentifier(),
                tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier(), tokenDTO.getApiKeyIdentifier(),
                tokenDTO.getIdentifier());
    Preconditions.checkState(
        optionalToken.isPresent(), "No token present with identifier: " + tokenDTO.getIdentifier());
    Token token = optionalToken.get();
    TokenDTO oldToken = TokenDTOMapper.getDTOFromToken(token);
    token.setName(tokenDTO.getName());
    token.setValidFrom(Instant.ofEpochMilli(tokenDTO.getValidFrom()));
    token.setValidTo(Instant.ofEpochMilli(tokenDTO.getValidTo()));
    token.setValidUntil(new Date(token.getExpiryTimestamp().toEpochMilli()));
    token.setDescription(tokenDTO.getDescription());
    token.setTags(TagMapper.convertToList(tokenDTO.getTags()));
    validate(token);

    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Token savedToken = tokenRepository.save(token);
      TokenDTO newToken = TokenDTOMapper.getDTOFromToken(savedToken);
      outboxService.save(new TokenUpdateEvent(oldToken, newToken));
      return newToken;
    }));
  }

  @Override
  public Map<String, Integer> getTokensPerApiKeyIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ApiKeyType apiKeyType, String parentIdentifier, List<String> apiKeyIdentifiers) {
    return tokenRepository.getTokensPerParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifiers);
  }

  @Override
  public PageResponse<TokenAggregateDTO> listAggregateTokens(
      String accountIdentifier, Pageable pageable, TokenFilterDTO filterDTO) {
    Criteria criteria =
        createApiKeyFilterCriteria(Criteria.where(TokenKeys.accountIdentifier).is(accountIdentifier), filterDTO);
    Page<Token> tokens = tokenRepository.findAll(criteria, pageable);
    return PageUtils.getNGPageResponse(tokens.map(token -> {
      TokenDTO tokenDTO = TokenDTOMapper.getDTOFromToken(token);
      return TokenAggregateDTO.builder()
          .token(tokenDTO)
          .expiryAt(token.getExpiryTimestamp().toEpochMilli())
          .createdAt(token.getCreatedAt())
          .lastModifiedAt(token.getLastModifiedAt())
          .build();
    }));
  }

  private Criteria createApiKeyFilterCriteria(Criteria criteria, TokenFilterDTO filterDTO) {
    if (filterDTO == null) {
      return criteria;
    }
    if (isNotBlank(filterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(TokenKeys.name).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(TokenKeys.identifier).regex(filterDTO.getSearchTerm(), "i"));
    }
    if (Objects.nonNull(filterDTO.getOrgIdentifier()) && !filterDTO.getOrgIdentifier().isEmpty()) {
      criteria.and(TokenKeys.orgIdentifier).is(filterDTO.getOrgIdentifier());
    }
    if (Objects.nonNull(filterDTO.getProjectIdentifier()) && !filterDTO.getProjectIdentifier().isEmpty()) {
      criteria.and(TokenKeys.projectIdentifier).is(filterDTO.getProjectIdentifier());
    }
    criteria.and(TokenKeys.apiKeyType).is(filterDTO.getApiKeyType());
    criteria.and(TokenKeys.parentIdentifier).is(filterDTO.getParentIdentifier());
    criteria.and(TokenKeys.apiKeyIdentifier).is(filterDTO.getApiKeyIdentifier());

    if (Objects.nonNull(filterDTO.getIdentifiers()) && !filterDTO.getIdentifiers().isEmpty()) {
      criteria.and(TokenKeys.identifier).in(filterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public long deleteAllByParentIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier) {
    return tokenRepository
        .deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
  }

  @Override
  public long deleteAllByApiKeyIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String apiKeyIdentifier) {
    return tokenRepository
        .deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndApiKeyIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, apiKeyIdentifier);
  }

  @Override
  public TokenDTO validateToken(String accountIdentifier, String apiKey) {
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_SUPPORT_JWT_TOKEN_SCIM_API)
        && jwtTokenAuthFilterHelper.isJWTTokenType(apiKey, accountIdentifier)) {
      return jwtTokenAuthFilterHelper.handleSCIMJwtTokenFlow(accountIdentifier, apiKey);
    } else {
      String tokenId = tokenValidationHelper.parseApiKeyToken(apiKey);
      TokenDTO tokenDTO = getToken(tokenId, true);
      tokenValidationHelper.validateToken(tokenDTO, accountIdentifier, tokenId, apiKey);
      tokenDTO.setEncodedPassword(null);
      return tokenDTO;
    }
  }
}
