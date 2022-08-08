/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.serviceaccounts.service.impl;

import static io.harness.accesscontrol.principals.PrincipalType.SERVICE_ACCOUNT;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.remote.client.NGRestUtils.getResponse;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.principals.PrincipalDTO;
import io.harness.accesscontrol.resourcegroups.api.ResourceGroupDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentAggregateResponseDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roles.api.RoleResponseDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectValidator;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.RoleAssignmentMetadataDTO;
import io.harness.ng.core.dto.ServiceAccountFilterDTO;
import io.harness.ng.core.events.ServiceAccountCreateEvent;
import io.harness.ng.core.events.ServiceAccountDeleteEvent;
import io.harness.ng.core.events.ServiceAccountUpdateEvent;
import io.harness.ng.serviceaccounts.dto.ServiceAccountAggregateDTO;
import io.harness.ng.serviceaccounts.entities.ServiceAccount;
import io.harness.ng.serviceaccounts.entities.ServiceAccount.ServiceAccountKeys;
import io.harness.ng.serviceaccounts.service.ServiceAccountDTOMapper;
import io.harness.ng.serviceaccounts.service.api.ServiceAccountService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ng.serviceaccounts.ServiceAccountRepository;
import io.harness.serviceaccount.ServiceAccountDTO;
import io.harness.utils.PageUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@OwnedBy(PL)
public class ServiceAccountServiceImpl implements ServiceAccountService {
  @Inject private ServiceAccountRepository serviceAccountRepository;
  @Inject private OutboxService outboxService;
  @Inject private AccountOrgProjectValidator accountOrgProjectValidator;
  @Inject private AccessControlAdminClient accessControlAdminClient;
  @Inject private ApiKeyService apiKeyService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private TokenService tokenService;

  @Override
  public ServiceAccountDTO createServiceAccount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ServiceAccountDTO requestDTO) {
    validateCreateServiceAccountRequest(accountIdentifier, orgIdentifier, projectIdentifier, requestDTO);
    ServiceAccount serviceAccount = ServiceAccountDTOMapper.getServiceAccountFromDTO(requestDTO);
    validate(serviceAccount);
    try {
      return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ServiceAccount savedAccount = serviceAccountRepository.save(serviceAccount);
        ServiceAccountDTO savedDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(savedAccount);
        outboxService.save(new ServiceAccountCreateEvent(savedDTO));
        return savedDTO;
      }));
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("A service account with identifier %s is already present or was deleted in scope",
              requestDTO.getIdentifier()),
          USER_SRE, ex);
    }
  }

  private void validateCreateServiceAccountRequest(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ServiceAccountDTO requestDTO) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(accountIdentifier, requestDTO.getAccountIdentifier()),
                               Pair.of(orgIdentifier, requestDTO.getOrgIdentifier()),
                               Pair.of(projectIdentifier, requestDTO.getProjectIdentifier())),
        true);
    if (!accountOrgProjectValidator.isPresent(accountIdentifier, orgIdentifier, projectIdentifier)) {
      throw new InvalidArgumentsException(String.format("Project [%s] in Org [%s] and Account [%s] does not exist",
                                              projectIdentifier, orgIdentifier, accountIdentifier),
          USER_SRE);
    }
  }

  @Override
  public ServiceAccountDTO updateServiceAccount(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, ServiceAccountDTO requestDTO) {
    ServiceAccount serviceAccount =
        serviceAccountRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (serviceAccount == null) {
      throw new InvalidRequestException(String.format("Service account with identifier: %s doesn't exist", identifier));
    }
    validateUpdateServiceAccountRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, requestDTO, serviceAccount);
    ServiceAccountDTO oldDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount);
    ServiceAccount newAccount = ServiceAccountDTOMapper.getServiceAccountFromDTO(requestDTO);
    newAccount.setUuid(serviceAccount.getUuid());
    newAccount.setCreatedAt(serviceAccount.getCreatedAt());
    validate(newAccount);
    return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      ServiceAccount savedAccount = serviceAccountRepository.save(newAccount);
      ServiceAccountDTO savedDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(savedAccount);
      outboxService.save(new ServiceAccountUpdateEvent(oldDTO, savedDTO));
      return savedDTO;
    }));
  }

  private void validateUpdateServiceAccountRequest(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, ServiceAccountDTO requestDTO, ServiceAccount serviceAccount) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(accountIdentifier, requestDTO.getAccountIdentifier()),
                               Pair.of(orgIdentifier, requestDTO.getOrgIdentifier()),
                               Pair.of(projectIdentifier, requestDTO.getProjectIdentifier()),
                               Pair.of(identifier, requestDTO.getIdentifier())),
        true);
    verifyValuesNotChanged(
        Lists.newArrayList(Pair.of(serviceAccount.getAccountIdentifier(), requestDTO.getAccountIdentifier()),
            Pair.of(serviceAccount.getOrgIdentifier(), requestDTO.getOrgIdentifier()),
            Pair.of(serviceAccount.getProjectIdentifier(), requestDTO.getProjectIdentifier()),
            Pair.of(serviceAccount.getEmail(), requestDTO.getEmail())),
        true);
  }

  @Override
  public boolean deleteServiceAccount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    ServiceAccount serviceAccount =
        serviceAccountRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (serviceAccount == null) {
      throw new InvalidRequestException(String.format("Service account with identifier: %s doesn't exist", identifier));
    }
    ServiceAccountDTO oldDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount);
    return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      long deleted =
          serviceAccountRepository.deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      if (deleted > 0) {
        outboxService.save(new ServiceAccountDeleteEvent(oldDTO));
        deleteApiKeysAndTokensForServiceAccount(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
        return true;
      } else {
        return false;
      }
    }));
  }

  private void deleteApiKeysAndTokensForServiceAccount(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    long deletedApis = apiKeyService.deleteAllByParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT, identifier);
    log.info(String.format("Deleted %d apis for service account %s", deletedApis, identifier));
    long deletedTokens = tokenService.deleteAllByParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT, identifier);
    log.info(String.format("Deleted %d tokens for service account %s", deletedTokens, identifier));
  }

  @Override
  public ServiceAccountDTO getServiceAccountDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    ServiceAccount serviceAccount =
        serviceAccountRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (serviceAccount == null) {
      throw new InvalidArgumentsException(String.format("Service account [%s] doesn't exist in scope", identifier));
    } else {
      return ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount);
    }
  }

  @Override
  public PageResponse<ServiceAccountAggregateDTO> listAggregateServiceAccounts(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<String> identifiers, Pageable pageable,
      ServiceAccountFilterDTO filterDTO) {
    Criteria criteria = createServiceAccountFilterCriteria(Criteria.where(ServiceAccountKeys.accountIdentifier)
                                                               .is(accountIdentifier)
                                                               .and(ServiceAccountKeys.projectIdentifier)
                                                               .is(null)
                                                               .and(ServiceAccountKeys.orgIdentifier)
                                                               .is(null),
        filterDTO);
    Page<ServiceAccount> serviceAccounts = serviceAccountRepository.findAll(criteria, pageable);
    List<String> saIdentifiers =
        serviceAccounts.stream().map(ServiceAccount::getIdentifier).distinct().collect(Collectors.toList());
    Map<String, List<RoleAssignmentMetadataDTO>> roleAssignmentsMap =
        getRoleAssignments(accountIdentifier, orgIdentifier, projectIdentifier, saIdentifiers);
    Map<String, Integer> apiKeysCountMap = apiKeyService.getApiKeysPerParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, ApiKeyType.SERVICE_ACCOUNT, saIdentifiers);

    return PageUtils.getNGPageResponse(serviceAccounts.map(serviceAccount -> {
      ServiceAccountDTO serviceAccountDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount);
      return ServiceAccountAggregateDTO.builder()
          .serviceAccount(serviceAccountDTO)
          .createdAt(serviceAccount.getCreatedAt())
          .lastModifiedAt(serviceAccount.getLastModifiedAt())
          .tokensCount(apiKeysCountMap.getOrDefault(serviceAccount.getIdentifier(), 0))
          .roleAssignmentsMetadataDTO(
              roleAssignmentsMap.getOrDefault(serviceAccount.getIdentifier(), new ArrayList<>()))
          .build();
    }));
  }

  private Map<String, List<RoleAssignmentMetadataDTO>> getRoleAssignments(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    Set<PrincipalDTO> principalDTOSet =
        identifiers.stream()
            .map(identifier
                -> PrincipalDTO.builder()
                       .identifier(identifier)
                       .type(SERVICE_ACCOUNT)
                       .scopeLevel(
                           ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier).toString().toLowerCase())
                       .build())
            .collect(Collectors.toSet());
    RoleAssignmentFilterDTO roleAssignmentFilterDTO =
        RoleAssignmentFilterDTO.builder().principalFilter(principalDTOSet).build();
    RoleAssignmentAggregateResponseDTO roleAssignmentAggregateResponseDTO =
        getResponse(accessControlAdminClient.getAggregatedFilteredRoleAssignments(
            accountIdentifier, orgIdentifier, projectIdentifier, roleAssignmentFilterDTO));
    Map<String, RoleResponseDTO> roleMap = roleAssignmentAggregateResponseDTO.getRoles().stream().collect(
        toMap(e -> e.getRole().getIdentifier(), Function.identity()));
    Map<String, ResourceGroupDTO> resourceGroupMap =
        roleAssignmentAggregateResponseDTO.getResourceGroups().stream().collect(
            toMap(ResourceGroupDTO::getIdentifier, Function.identity()));

    return roleAssignmentAggregateResponseDTO.getRoleAssignments()
        .stream()
        .filter(roleAssignmentDTO
            -> roleMap.containsKey(roleAssignmentDTO.getRoleIdentifier())
                && resourceGroupMap.containsKey(roleAssignmentDTO.getResourceGroupIdentifier()))
        .collect(Collectors.groupingBy(roleAssignment
            -> roleAssignment.getPrincipal().getIdentifier(),
            Collectors.mapping(roleAssignment
                -> RoleAssignmentMetadataDTO.builder()
                       .identifier(roleAssignment.getIdentifier())
                       .roleIdentifier(roleAssignment.getRoleIdentifier())
                       .resourceGroupIdentifier(roleAssignment.getResourceGroupIdentifier())
                       .roleName(roleMap.get(roleAssignment.getRoleIdentifier()).getRole().getName())
                       .resourceGroupName(resourceGroupMap.get(roleAssignment.getResourceGroupIdentifier()).getName())
                       .managedRole(roleMap.get(roleAssignment.getRoleIdentifier()).isHarnessManaged())
                       .managedRoleAssignment(roleAssignment.isManaged())
                       .build(),
                toList())));
  }

  @Override
  public List<ServiceAccountDTO> listServiceAccounts(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers) {
    List<ServiceAccount> serviceAccounts;
    if (identifiers.isEmpty()) {
      serviceAccounts = serviceAccountRepository.findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifier(
          accountIdentifier, orgIdentifier, projectIdentifier);
    } else {
      serviceAccounts =
          serviceAccountRepository.findAllByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifierIsIn(
              accountIdentifier, orgIdentifier, projectIdentifier, identifiers);
    }

    List<ServiceAccountDTO> serviceAccountDTOS = new ArrayList<>();
    if (isNotEmpty(serviceAccounts)) {
      serviceAccounts.forEach(
          serviceAccount -> serviceAccountDTOS.add(ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount)));
    }
    return serviceAccountDTOS;
  }

  private Criteria createServiceAccountFilterCriteria(
      Criteria criteria, ServiceAccountFilterDTO serviceAccountFilterDTO) {
    if (serviceAccountFilterDTO == null) {
      return criteria;
    }
    if (isNotBlank(serviceAccountFilterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(ServiceAccountKeys.name).regex(serviceAccountFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ServiceAccountKeys.identifier).regex(serviceAccountFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ServiceAccountKeys.tags + "." + NGTagKeys.key)
              .regex(serviceAccountFilterDTO.getSearchTerm(), "i"),
          Criteria.where(ServiceAccountKeys.tags + "." + NGTagKeys.value)
              .regex(serviceAccountFilterDTO.getSearchTerm(), "i"));
    }
    if (Objects.nonNull(serviceAccountFilterDTO.getOrgIdentifier())
        && !serviceAccountFilterDTO.getOrgIdentifier().isEmpty()) {
      criteria.and(ServiceAccountKeys.orgIdentifier).in(serviceAccountFilterDTO.getOrgIdentifier());
    }
    if (Objects.nonNull(serviceAccountFilterDTO.getProjectIdentifier())
        && !serviceAccountFilterDTO.getProjectIdentifier().isEmpty()) {
      criteria.and(ServiceAccountKeys.projectIdentifier).in(serviceAccountFilterDTO.getProjectIdentifier());
    }
    if (Objects.nonNull(serviceAccountFilterDTO.getIdentifiers())
        && !serviceAccountFilterDTO.getIdentifiers().isEmpty()) {
      criteria.and(ServiceAccountKeys.identifier).in(serviceAccountFilterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public ServiceAccountAggregateDTO getServiceAccountAggregateDTO(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    ServiceAccount serviceAccount =
        serviceAccountRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (serviceAccount == null) {
      throw new InvalidArgumentsException(String.format("Service account [%s] doesn't exist in scope", identifier));
    }
    ServiceAccountDTO serviceAccountDTO = ServiceAccountDTOMapper.getDTOFromServiceAccount(serviceAccount);
    Map<String, List<RoleAssignmentMetadataDTO>> roleAssignmentsMap =
        getRoleAssignments(accountIdentifier, orgIdentifier, projectIdentifier, Collections.singletonList(identifier));
    Map<String, Integer> apiKeysCountMap = apiKeyService.getApiKeysPerParentIdentifier(accountIdentifier, orgIdentifier,
        projectIdentifier, ApiKeyType.SERVICE_ACCOUNT, Collections.singletonList(identifier));
    return ServiceAccountAggregateDTO.builder()
        .serviceAccount(serviceAccountDTO)
        .createdAt(serviceAccount.getCreatedAt())
        .lastModifiedAt(serviceAccount.getLastModifiedAt())
        .tokensCount(apiKeysCountMap.getOrDefault(serviceAccount.getIdentifier(), 0))
        .roleAssignmentsMetadataDTO(roleAssignmentsMap.getOrDefault(serviceAccount.getIdentifier(), new ArrayList<>()))
        .build();
  }
}
