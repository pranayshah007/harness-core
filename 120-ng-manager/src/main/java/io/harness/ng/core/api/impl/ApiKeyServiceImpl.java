/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.core.account.ServiceAccountConfig.DEFAULT_API_KEY_LIMIT;
import static io.harness.ng.core.account.ServiceAccountConfig.DEFAULT_JWT_VERIFICATION_API_KEY_LIMIT;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.accesscontrol.PlatformResourceTypes;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.ApiKeyAggregateDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.ApiKeyFilterDTO;
import io.harness.ng.core.dto.JwtVerificationApiKeyDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.ApiKey.ApiKeyKeys;
import io.harness.ng.core.events.ApiKeyCreateEvent;
import io.harness.ng.core.events.ApiKeyDeleteEvent;
import io.harness.ng.core.events.ApiKeyUpdateEvent;
import io.harness.ng.core.mapper.ApiKeyDTOMapper;
import io.harness.ng.core.mapper.JwtVerificationApiKeyMapper;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.ApiKeyRepository;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.io.IOUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class ApiKeyServiceImpl implements ApiKeyService {
  @Inject private ApiKeyRepository apiKeyRepository;
  @Inject private OutboxService outboxService;
  @Inject private AccountOrgProjectValidator accountOrgProjectValidator;
  @Inject private TokenService tokenService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private AccessControlClient accessControlClient;
  @Inject private AccountService accountService;

  @Override
  public ApiKeyDTO createApiKey(ApiKeyDTO apiKeyDTO) {
    validateApiKeyRequest(
        apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier());
    validateApiKeyLimit(apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(),
        apiKeyDTO.getProjectIdentifier(), apiKeyDTO.getParentIdentifier(), apiKeyDTO.getApiKeyType());
    try {
      ApiKey apiKey = ApiKeyDTOMapper.getApiKeyFromDTO(apiKeyDTO);
      validate(apiKey);
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ApiKey savedApiKey = apiKeyRepository.save(apiKey);
        ApiKeyDTO savedDTO = ApiKeyDTOMapper.getDTOFromApiKey(savedApiKey);
        outboxService.save(new ApiKeyCreateEvent(savedDTO));
        return savedDTO;
      }));
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("Try using different Key name, [%s] already exists", apiKeyDTO.getIdentifier()));
    }
  }

  private void validateApiKeyRequest(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (!accountOrgProjectValidator.isPresent(accountIdentifier, orgIdentifier, projectIdentifier)) {
      throw new InvalidArgumentsException(String.format("Project [%s] in Org [%s] and Account [%s] does not exist",
                                              accountIdentifier, orgIdentifier, projectIdentifier),
          USER_SRE);
    }
  }

  private void validateApiKeyLimit(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentIdentifier, ApiKeyType keyType) {
    ServiceAccountConfig serviceAccountConfig = accountService.getAccount(accountIdentifier).getServiceAccountConfig();
    long apiKeyLimit;
    if (ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION == keyType) {
      apiKeyLimit = serviceAccountConfig != null ? serviceAccountConfig.getJwtVerificationApiKeyLimit()
                                                 : DEFAULT_JWT_VERIFICATION_API_KEY_LIMIT;
    } else {
      apiKeyLimit = serviceAccountConfig != null ? serviceAccountConfig.getApiKeyLimit() : DEFAULT_API_KEY_LIMIT;
    }
    long existingAPIKeyCount =
        apiKeyRepository.countByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifierAndApiKeyType(
            accountIdentifier, orgIdentifier, projectIdentifier, parentIdentifier, keyType);
    if (existingAPIKeyCount >= apiKeyLimit) {
      throw new InvalidRequestException(
          String.format("Maximum limit reached for creating API key of type %s", keyType.name()));
    }
  }

  @Override
  public ApiKeyDTO updateApiKey(ApiKeyDTO apiKeyDTO) {
    validateApiKeyRequest(
        apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier());
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier(),
                apiKeyDTO.getApiKeyType(), apiKeyDTO.getParentIdentifier(), apiKeyDTO.getIdentifier());
    Preconditions.checkState(
        optionalApiKey.isPresent(), "Api key not present in scope for identifier: " + apiKeyDTO.getIdentifier());
    ApiKey existingKey = optionalApiKey.get();
    ApiKeyDTO existingDTO = ApiKeyDTOMapper.getDTOFromApiKey(existingKey);
    ApiKey newKey = ApiKeyDTOMapper.getApiKeyFromDTO(apiKeyDTO);
    newKey.setUuid(existingKey.getUuid());
    newKey.setCreatedAt(existingKey.getCreatedAt());
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ApiKey savedApiKey = apiKeyRepository.save(newKey);
      ApiKeyDTO savedDTO = ApiKeyDTOMapper.getDTOFromApiKey(savedApiKey);
      outboxService.save(new ApiKeyUpdateEvent(existingDTO, savedDTO));
      return savedDTO;
    }));
  }

  @Override
  public boolean deleteApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    validateApiKeyRequest(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
    Preconditions.checkState(optionalApiKey.isPresent(), "Api key not present in scope for identifier: ", identifier);
    ApiKeyDTO existingDTO = ApiKeyDTOMapper.getDTOFromApiKey(optionalApiKey.get());
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      long deleted =
          apiKeyRepository
              .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                  accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
      if (deleted > 0) {
        outboxService.save(new ApiKeyDeleteEvent(existingDTO));
        tokenService.deleteAllByApiKeyIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
        return true;
      } else {
        return false;
      }
    }));
  }

  @Override
  public List<ApiKeyDTO> listApiKeys(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, List<String> identifiers) {
    List<ApiKey> apiKeys;
    if (isEmpty(identifiers)) {
      apiKeys = apiKeyRepository
                    .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifier(
                        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    } else {
      apiKeys =
          apiKeyRepository
              .findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifierIn(
                  accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifiers);
    }
    List<ApiKeyDTO> apiKeyDTOS = new ArrayList<>();
    apiKeys.forEach(apiKey -> apiKeyDTOS.add(ApiKeyDTOMapper.getDTOFromApiKey(apiKey)));
    return apiKeyDTOS;
  }

  @Override
  public ApiKey getApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
    if (optionalApiKey.isPresent()) {
      return optionalApiKey.get();
    }
    throw new InvalidRequestException(String.format("Api key not present in scope for identifier: [%s]", identifier));
  }

  @Override
  public Map<String, Integer> getApiKeysPerParentIdentifier(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ApiKeyType apiKeyType, List<String> parentIdentifier) {
    return apiKeyRepository.getApiKeysPerParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
  }

  @Override
  public PageResponse<ApiKeyAggregateDTO> listAggregateApiKeys(
      String accountIdentifier, Pageable pageable, ApiKeyFilterDTO filterDTO) {
    Criteria criteria =
        createApiKeyFilterCriteria(Criteria.where(ApiKeyKeys.accountIdentifier).is(accountIdentifier), filterDTO);
    Page<ApiKey> apiKeys = apiKeyRepository.findAll(criteria, pageable);
    List<String> apiKeyIdentifiers =
        apiKeys.stream().map(ApiKey::getIdentifier).distinct().collect(Collectors.toList());
    Map<String, Integer> tokenCountMap = tokenService.getTokensPerApiKeyIdentifier(accountIdentifier,
        filterDTO.getOrgIdentifier(), filterDTO.getProjectIdentifier(), filterDTO.getApiKeyType(),
        filterDTO.getParentIdentifier(), apiKeyIdentifiers);
    return PageUtils.getNGPageResponse(apiKeys.map(apiKey -> {
      ApiKeyDTO apiKeyDTO = ApiKeyDTOMapper.getDTOFromApiKey(apiKey);
      return ApiKeyAggregateDTO.builder()
          .apiKey(apiKeyDTO)
          .createdAt(apiKey.getCreatedAt())
          .lastModifiedAt(apiKey.getLastModifiedAt())
          .tokensCount(tokenCountMap.getOrDefault(apiKey.getIdentifier(), 0))
          .build();
    }));
  }

  private Criteria createApiKeyFilterCriteria(Criteria criteria, ApiKeyFilterDTO filterDTO) {
    if (filterDTO == null) {
      return criteria;
    }
    if (isNotBlank(filterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(ApiKeyKeys.name).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(ApiKeyKeys.identifier).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(ApiKeyKeys.tags + "." + NGTagKeys.key).regex(filterDTO.getSearchTerm(), "i"),
          Criteria.where(ApiKeyKeys.tags + "." + NGTagKeys.value).regex(filterDTO.getSearchTerm(), "i"));
    }
    if (Objects.nonNull(filterDTO.getOrgIdentifier()) && !filterDTO.getOrgIdentifier().isEmpty()) {
      criteria.and(ApiKeyKeys.orgIdentifier).is(filterDTO.getOrgIdentifier());
    }
    if (Objects.nonNull(filterDTO.getProjectIdentifier()) && !filterDTO.getProjectIdentifier().isEmpty()) {
      criteria.and(ApiKeyKeys.projectIdentifier).is(filterDTO.getProjectIdentifier());
    }
    criteria.and(ApiKeyKeys.apiKeyType).is(filterDTO.getApiKeyType());
    criteria.and(ApiKeyKeys.parentIdentifier).is(filterDTO.getParentIdentifier());

    if (Objects.nonNull(filterDTO.getIdentifiers()) && !filterDTO.getIdentifiers().isEmpty()) {
      criteria.and(ApiKeyKeys.identifier).in(filterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public ApiKeyAggregateDTO getApiKeyAggregateDTO(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    Optional<ApiKey> apiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
    if (!apiKey.isPresent()) {
      throw new InvalidArgumentsException(String.format("Api key [%s] doesn't exist in scope", identifier));
    }
    ApiKeyDTO apiKeyDTO = ApiKeyDTOMapper.getDTOFromApiKey(apiKey.get());
    Map<String, Integer> tokenCountMap = tokenService.getTokensPerApiKeyIdentifier(accountIdentifier, orgIdentifier,
        projectIdentifier, apiKeyType, parentIdentifier, Collections.singletonList(identifier));
    return ApiKeyAggregateDTO.builder()
        .apiKey(apiKeyDTO)
        .createdAt(apiKey.get().getCreatedAt())
        .lastModifiedAt(apiKey.get().getLastModifiedAt())
        .tokensCount(tokenCountMap.getOrDefault(apiKeyDTO.getIdentifier(), 0))
        .build();
  }

  @Override
  public long deleteAllByParentIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier) {
    return apiKeyRepository
        .deleteAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
  }

  @Override
  public void validateParentIdentifier(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier) {
    switch (apiKeyType) {
      case USER:
        java.util.Optional<String> userId = java.util.Optional.empty();
        if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
            && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
          userId = java.util.Optional.of(SourcePrincipalContextBuilder.getSourcePrincipal().getName());
        }
        if (!userId.isPresent()) {
          throw new InvalidArgumentsException("No user identifier present in context");
        }
        if (!userId.get().equals(parentIdentifier)) {
          throw new InvalidArgumentsException(String.format(
              "User [%s] not authenticated to create api key for user [%s]", userId.get(), parentIdentifier));
        }
        break;
      case SERVICE_ACCOUNT:
      case SERVICE_ACCOUNT_JWT_VERIFICATION:
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(PlatformResourceTypes.SERVICEACCOUNT, parentIdentifier),
            MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);
        break;
      default:
        throw new InvalidArgumentsException(String.format("Invalid api key type: %s", apiKeyType));
    }
  }

  @Override
  public JwtVerificationApiKeyDTO createJwtVerificationApiKey(@NotNull JwtVerificationApiKeyDTO apiKeyDTO,
      @NotNull String accountIdentifier, @NotNull InputStream inputStream) {
    validateApiKeyRequest(
        apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier());
    validateApiKeyLimit(apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(),
        apiKeyDTO.getProjectIdentifier(), apiKeyDTO.getParentIdentifier(), ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION);
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository.findByAccountIdentifierAndKeyFieldAndValueFieldAndApiKeyType(accountIdentifier, apiKeyDTO.getKeyField(), apiKeyDTO.getValueField(), ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION);
    Preconditions.checkState(optionalApiKey.isEmpty(),
        "Api key with same field {}, and value {} is present in scope for account {}",
        apiKeyDTO.getKeyField(), apiKeyDTO.getValueField(), apiKeyDTO.getAccountIdentifier());
    try {
      ApiKey apiKey = JwtVerificationApiKeyMapper.toApiKey(apiKeyDTO);
      if (null != inputStream) {
        String fileAsString = IOUtils.toString(inputStream, Charset.defaultCharset());
        apiKey.setCertificate(fileAsString);
      }
      validate(apiKey);
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ApiKey savedApiKey = apiKeyRepository.save(apiKey);
        return JwtVerificationApiKeyMapper.toJwtVerificationApiKeyDTO(savedApiKey);
        // TODO: @pbarapatre10 add events and audits 'outboxService.save(new ApiKeyCreateEvent(savedDTO));'
      }));
    } catch (DuplicateKeyException e) {
      throw new DuplicateFieldException(
          String.format("Try using different key name or identifier, [%s] already exists", apiKeyDTO.getIdentifier()));
    } catch (IOException ioExc) {
      throw new InvalidRequestException(
          "Could not read and process supplied certificate file at account: " + accountIdentifier);
    }
  }

  @Override
  public JwtVerificationApiKeyDTO updateJwtVerificationApiKey(
      JwtVerificationApiKeyDTO apiKeyDTO, String accountIdentifier, InputStream inputStream) {
    validateApiKeyRequest(
        apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier());
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier(),
                apiKeyDTO.getApiKeyType(), apiKeyDTO.getParentIdentifier(), apiKeyDTO.getIdentifier());
    Preconditions.checkState(optionalApiKey.isPresent(),
        "Api key not present in scope for identifier {}: account {}, organization {}, project {}, parentId {}",
        apiKeyDTO.getIdentifier(), apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(),
        apiKeyDTO.getProjectIdentifier(), apiKeyDTO.getParentIdentifier());
    ApiKey existingKey = optionalApiKey.get();
    ApiKey newKey = JwtVerificationApiKeyMapper.toApiKey(apiKeyDTO);
    if (inputStream == null && isEmpty(apiKeyDTO.getCertificateUrl()) && isNotEmpty(existingKey.getCertificate())) {
      newKey.setCertificate(existingKey.getCertificate());
    } else {
      try {
        if (null != inputStream) {
          String fileAsString = IOUtils.toString(inputStream, Charset.defaultCharset());
          newKey.setCertificate(fileAsString);
        }
      } catch (IOException ioExc) {
        throw new InvalidRequestException(
            "Could not read and process supplied certificate file at account: " + accountIdentifier);
      }
    }
    newKey.setUuid(existingKey.getUuid());
    newKey.setCreatedAt(existingKey.getCreatedAt());
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ApiKey savedApiKey = apiKeyRepository.save(newKey);
      return JwtVerificationApiKeyMapper.toJwtVerificationApiKeyDTO(savedApiKey);
      // TODO: @pbarapatre10 add events and audits 'outboxService.save(new ApiKeyUpdateEvent(existingDTO, savedDTO));'
    }));
  }

  @Override
  public boolean deleteJwtVerificationApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String parentIdentifier, String identifier) {
    validateApiKeyRequest(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<ApiKey> optionalApiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION,
                parentIdentifier, identifier);
    Preconditions.checkState(optionalApiKey.isPresent(),
        "Api key not present in scope for identifier: {}, account: {}, organization: {}, project: {}, parent: {}",
        identifier, accountIdentifier, orgIdentifier, parentIdentifier, parentIdentifier);
    // ApiKeyDTO existingDTO = ApiKeyDTOMapper.getDTOFromApiKey(optionalApiKey.get());
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      long deleted =
          apiKeyRepository
              .deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                  accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION,
                  parentIdentifier, identifier);
      if (deleted > 0) {
        // TODO: @pbarapatre10 add events and audits 'outboxService.save(new ApiKeyDeleteEvent(existingDTO));'
        tokenService.deleteAllByApiKeyIdentifier(accountIdentifier, orgIdentifier, projectIdentifier,
            ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION, parentIdentifier, identifier);
        return true;
      } else {
        return false;
      }
    }));
  }

  @Override
  public JwtVerificationApiKeyDTO getJwtVerificationApiKey(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String parentIdentifier, String identifier) {
    Optional<ApiKey> apiKey =
        apiKeyRepository
            .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT_JWT_VERIFICATION,
                parentIdentifier, identifier);
    if (apiKey.isEmpty()) {
      throw new InvalidArgumentsException(String.format(
          "Api key with identifier %s, doesn't exist in scope: account %s, organization %s, project %s, parent %s",
          identifier, accountIdentifier, orgIdentifier, parentIdentifier, parentIdentifier));
    }
    return JwtVerificationApiKeyMapper.toJwtVerificationApiKeyDTO(apiKey.get());
  }

  @Override
  public PageResponse<JwtVerificationApiKeyDTO> listJwtVerificationApiKeys(
      String accountIdentifier, Pageable pageable, ApiKeyFilterDTO filterDTO) {
    Criteria criteria =
        createApiKeyFilterCriteria(Criteria.where(ApiKeyKeys.accountIdentifier).is(accountIdentifier), filterDTO);
    Page<ApiKey> apiKeys = apiKeyRepository.findAll(criteria, pageable);
    return PageUtils.getNGPageResponse(apiKeys.map(JwtVerificationApiKeyMapper::toJwtVerificationApiKeyDTO));
  }
}
