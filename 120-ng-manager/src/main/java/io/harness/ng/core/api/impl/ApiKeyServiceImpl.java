/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.core.account.ServiceAccountConfig.DEFAULT_API_KEY_LIMIT;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
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
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.entities.ApiKey;
import io.harness.ng.core.entities.ApiKey.ApiKeyKeys;
import io.harness.ng.core.events.ApiKeyCreateEvent;
import io.harness.ng.core.events.ApiKeyDeleteEvent;
import io.harness.ng.core.events.ApiKeyUpdateEvent;
import io.harness.ng.core.mapper.ApiKeyDTOMapper;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.core.spring.ApiKeyRepository;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
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
  @Inject private NgUserService ngUserService;

  @Override
  public ApiKeyDTO createApiKey(ApiKeyDTO apiKeyDTO) {
    validateApiKeyRequest(
        apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(), apiKeyDTO.getProjectIdentifier());
    validateApiKeyLimit(apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(),
        apiKeyDTO.getProjectIdentifier(), apiKeyDTO.getParentIdentifier());
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

  private void validateApiKeyLimit(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String parentIdentifier) {
    ServiceAccountConfig serviceAccountConfig = accountService.getAccount(accountIdentifier).getServiceAccountConfig();
    long apiKeyLimit = serviceAccountConfig != null ? serviceAccountConfig.getApiKeyLimit() : DEFAULT_API_KEY_LIMIT;
    long existingAPIKeyCount =
        apiKeyRepository.countByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndParentIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, parentIdentifier);
    if (existingAPIKeyCount >= apiKeyLimit) {
      throw new InvalidRequestException(String.format("Maximum limit has reached"));
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
  public Optional<ApiKey> getApiKey(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ApiKeyType apiKeyType, String parentIdentifier, String identifier) {
    return apiKeyRepository
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndApiKeyTypeAndParentIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
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

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(ApiKeyKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(ApiKeyKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(ApiKeyKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }

  @Override
  public void deleteAtAllScopes(Scope scope) {
    Criteria criteria =
        createScopeCriteria(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    apiKeyRepository.deleteAll(criteria);
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
        if (userId.isEmpty()) {
          throw new InvalidArgumentsException("No user identifier present in context");
        }
        if (!userId.get().equals(parentIdentifier)) {
          throw new InvalidArgumentsException(String.format(
              "User [%s] not authenticated to create or list api key for user [%s]", userId.get(), parentIdentifier));
        }
        Optional<UserInfo> userInfo = ngUserService.getUserById(userId.get());
        if (userInfo.isEmpty()) {
          throw new InvalidArgumentsException(String.format("No user found with id: [%s]", userId.get()));
        }

        List<GatewayAccountRequestDTO> userAccounts = userInfo.get().getAccounts();

        if (userAccounts == null
            || userAccounts.stream()
                   .filter(account -> account.getUuid().equals(accountIdentifier))
                   .findFirst()
                   .isEmpty()) {
          throw new InvalidArgumentsException(String.format(
              "User [%s] is not authorized to create ApiKey for account: [%s]", userId.get(), accountIdentifier));
        }

        break;
      case SERVICE_ACCOUNT:
        accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
            Resource.of(PlatformResourceTypes.SERVICEACCOUNT, parentIdentifier),
            MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);

        break;
      default:
        throw new InvalidArgumentsException(String.format("Invalid api key type: %s", apiKeyType));
    }
  }

  @Override
  public Long countApiKeys(String accountIdentifier) {
    return apiKeyRepository.countByAccountIdentifier(accountIdentifier);
  }
}
