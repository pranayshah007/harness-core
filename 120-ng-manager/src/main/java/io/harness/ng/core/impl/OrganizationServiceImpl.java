/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.impl;

import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.enforcement.constants.FeatureRestrictionName.MULTIPLE_ORGANIZATIONS;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ng.accesscontrol.PlatformPermissions.INVITE_PERMISSION_IDENTIFIER;
import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.ng.core.user.UserMembershipUpdateSource.SYSTEM;
import static io.harness.ng.core.utils.NGUtils.validate;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.api.DefaultUserGroupService;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.events.OrganizationCreateEvent;
import io.harness.ng.core.events.OrganizationDeleteEvent;
import io.harness.ng.core.events.OrganizationRestoreEvent;
import io.harness.ng.core.events.OrganizationUpdateEvent;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.remote.OrganizationMapper;
import io.harness.ng.core.remote.utils.ScopeAccessHelper;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.core.spring.OrganizationRepository;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.telemetry.helpers.OrganizationInstrumentationHelper;
import io.harness.utils.ScopeUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Singleton
@Slf4j
public class OrganizationServiceImpl implements OrganizationService {
  private static final String ORG_ADMIN_ROLE = "_organization_admin";
  private final OrganizationRepository organizationRepository;
  private final OutboxService outboxService;
  private final TransactionTemplate transactionTemplate;
  private final NgUserService ngUserService;
  private final AccessControlClient accessControlClient;
  private final ScopeAccessHelper scopeAccessHelper;
  private final OrganizationInstrumentationHelper instrumentationHelper;
  private final DefaultUserGroupService defaultUserGroupService;

  @Inject
  public OrganizationServiceImpl(OrganizationRepository organizationRepository, OutboxService outboxService,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, NgUserService ngUserService,
      AccessControlClient accessControlClient, ScopeAccessHelper scopeAccessHelper,
      OrganizationInstrumentationHelper instrumentationHelper, DefaultUserGroupService defaultUserGroupService) {
    this.organizationRepository = organizationRepository;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.ngUserService = ngUserService;
    this.accessControlClient = accessControlClient;
    this.scopeAccessHelper = scopeAccessHelper;
    this.instrumentationHelper = instrumentationHelper;
    this.defaultUserGroupService = defaultUserGroupService;
  }

  @Override
  @FeatureRestrictionCheck(MULTIPLE_ORGANIZATIONS)
  public Organization create(@AccountIdentifier String accountIdentifier, OrganizationDTO organizationDTO) {
    Organization organization = toOrganization(organizationDTO);
    organization.setAccountIdentifier(accountIdentifier);
    try {
      validate(organization);
      Organization savedOrganization = saveOrganization(organization);
      setupOrganization(Scope.of(accountIdentifier, organizationDTO.getIdentifier(), null));
      log.info(
          String.format("Organization with identifier [%s] was successfully created", organization.getIdentifier()));
      instrumentationHelper.sendOrganizationCreateEvent(organization, accountIdentifier);
      return savedOrganization;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          String.format("An organization with identifier [%s] is already present", organization.getIdentifier()),
          USER_SRE, ex);
    }
  }

  @Override
  public List<String> getDistinctAccounts() {
    return organizationRepository.findDistinctAccounts();
  }

  private void setupOrganization(Scope scope) {
    try {
      defaultUserGroupService.create(scope, emptyList());
    } catch (Exception ex) {
      log.error("Default User Group Creation failed for Organization: " + scope.toString(), ex);
    }
    if (DEFAULT_ORG_IDENTIFIER.equals(scope.getOrgIdentifier())) {
      // Default org is a special case. That is handled by default org service
      return;
    }
    String principalId = null;
    PrincipalType principalType = PrincipalType.USER;
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && (SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER
            || SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.SERVICE_ACCOUNT)) {
      principalId = SourcePrincipalContextBuilder.getSourcePrincipal().getName();
      principalType = SourcePrincipalContextBuilder.getSourcePrincipal().getType();
    }
    // in case of default org identifier userprincipal will not be set in security context and that is okay
    if (isEmpty(principalId)) {
      throw new InvalidRequestException("User not found in security context");
    }
    try {
      assignOrgAdmin(scope, principalId, principalType);
      busyPollUntilOrgSetupCompletes(scope, principalId);
    } catch (Exception e) {
      log.error("Failed to complete post organization creation steps for [{}]", ScopeUtils.toString(scope));
    }
  }

  private void busyPollUntilOrgSetupCompletes(Scope scope, String userId) {
    RetryConfig config = RetryConfig.custom()
                             .maxAttempts(50)
                             .waitDuration(Duration.ofMillis(200))
                             .retryOnResult(Boolean.FALSE::equals)
                             .retryExceptions(Exception.class)
                             .ignoreExceptions(IOException.class)
                             .build();
    Retry retry = Retry.of("check user permissions", config);
    Retry.EventPublisher publisher = retry.getEventPublisher();
    publisher.onRetry(event -> log.info("Retrying for organization {} {}", scope.getOrgIdentifier(), event.toString()));
    publisher.onSuccess(
        event -> log.info("Retrying for organization {} {}", scope.getOrgIdentifier(), event.toString()));
    Supplier<Boolean> hasAccess = Retry.decorateSupplier(retry,
        ()
            -> accessControlClient.hasAccess(
                ResourceScope.of(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier()),
                Resource.of("USER", userId), INVITE_PERMISSION_IDENTIFIER));
    if (FALSE.equals(hasAccess.get())) {
      log.error(
          "Finishing organization setup without confirm role assignment creation [{}]", ScopeUtils.toString(scope));
    }
  }

  private void assignOrgAdmin(Scope scope, String principalId, PrincipalType principalType) {
    switch (principalType) {
      case USER:
        ngUserService.addUserToScope(principalId,
            Scope.builder()
                .accountIdentifier(scope.getAccountIdentifier())
                .orgIdentifier(scope.getOrgIdentifier())
                .projectIdentifier(scope.getProjectIdentifier())
                .build(),
            singletonList(RoleBinding.builder()
                              .roleIdentifier(ORG_ADMIN_ROLE)
                              .resourceGroupIdentifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
                              .build()),
            emptyList(), SYSTEM);
        break;
      case SERVICE_ACCOUNT:
        ngUserService.addServiceAccountToScope(principalId,
            Scope.builder()
                .accountIdentifier(scope.getAccountIdentifier())
                .orgIdentifier(scope.getOrgIdentifier())
                .projectIdentifier(scope.getProjectIdentifier())
                .build(),
            RoleBinding.builder()
                .roleIdentifier(ORG_ADMIN_ROLE)
                .resourceGroupIdentifier(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER)
                .build(),
            SYSTEM);
        break;
      case API_KEY:
      case SERVICE: {
        throw new InvalidRequestException(
            "Cannot assign principal" + principalId + "with type" + principalType + "to org");
      }
      default: {
        throw new InvalidRequestException("Invalid  principal type" + principalType);
      }
    }
  }

  private Organization saveOrganization(Organization organization) {
    if (DEFAULT_ORG_IDENTIFIER.equalsIgnoreCase(organization.getIdentifier())) {
      log.info("[AccountSetup]: Creating Default Organization");
    }
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Organization savedOrganization = organizationRepository.save(organization);
      outboxService.save(
          new OrganizationCreateEvent(organization.getAccountIdentifier(), OrganizationMapper.writeDto(organization)));
      if (DEFAULT_ORG_IDENTIFIER.equalsIgnoreCase(organization.getIdentifier())) {
        log.info("[AccountSetup]: Default Organization created Successfully");
      }
      return savedOrganization;
    }));
  }

  @Override
  public Optional<Organization> get(String accountIdentifier, String organizationIdentifier) {
    return organizationRepository.findByAccountIdentifierAndIdentifierIgnoreCaseAndDeletedNot(
        accountIdentifier, organizationIdentifier, true);
  }

  @Override
  public Optional<Organization> getConsideringCase(String accountIdentifier, String organizationIdentifier) {
    return organizationRepository.findByAccountIdentifierAndIdentifierAndDeletedNot(
        accountIdentifier, organizationIdentifier, true);
  }

  @Override
  public Organization update(String accountIdentifier, String identifier, OrganizationDTO organizationDTO) {
    validateUpdateOrganizationRequest(identifier, organizationDTO);
    Optional<Organization> optionalOrganization = get(accountIdentifier, identifier);

    if (optionalOrganization.isPresent()) {
      Organization existingOrganization = optionalOrganization.get();
      if (Boolean.TRUE.equals(existingOrganization.getHarnessManaged())) {
        throw new InvalidRequestException(
            String.format("Update operation not supported for Default Organization (identifier: [%s])", identifier),
            WingsException.USER);
      }
      Organization organization = toOrganization(organizationDTO);
      organization.setAccountIdentifier(accountIdentifier);
      organization.setId(existingOrganization.getId());
      if (organization.getVersion() == null) {
        organization.setVersion(existingOrganization.getVersion());
      }
      validate(organization);
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Organization updatedOrganization = organizationRepository.save(organization);
        log.info(String.format("Organization with identifier [%s] was successfully updated", identifier));
        outboxService.save(new OrganizationUpdateEvent(organization.getAccountIdentifier(),
            OrganizationMapper.writeDto(updatedOrganization), OrganizationMapper.writeDto(existingOrganization)));
        return updatedOrganization;
      }));
    }
    throw new InvalidRequestException(
        String.format("Organisation with identifier [%s] not found", identifier), WingsException.USER);
  }

  @Override
  public Page<Organization> listPermittedOrgs(
      String accountIdentifier, Pageable pageable, OrganizationFilterDTO organizationFilterDTO) {
    Criteria criteria = createOrganizationFilterCriteria(Criteria.where(OrganizationKeys.accountIdentifier)
                                                             .is(accountIdentifier)
                                                             .and(OrganizationKeys.deleted)
                                                             .is(FALSE),
        organizationFilterDTO);
    List<Scope> orgs = organizationRepository.findAllOrgs(criteria);
    List<String> permittedOrgsIds =
        scopeAccessHelper.getPermittedScopes(orgs).stream().map(Scope::getOrgIdentifier).collect(Collectors.toList());

    if (permittedOrgsIds.isEmpty()) {
      return Page.empty();
    }

    // Update the identifiers in the organizationFilterDTO
    if (organizationFilterDTO != null) {
      organizationFilterDTO.setIdentifiers(permittedOrgsIds);
    } else {
      organizationFilterDTO = OrganizationFilterDTO.builder().identifiers(permittedOrgsIds).build();
    }
    Criteria criteriaForPermittedOrgs =
        createOrganizationFilterCriteria(Criteria.where(OrganizationKeys.accountIdentifier)
                                             .is(accountIdentifier)
                                             .and(OrganizationKeys.deleted)
                                             .is(FALSE),
            organizationFilterDTO);

    return organizationRepository.findAll(
        criteriaForPermittedOrgs, pageable, organizationFilterDTO != null && organizationFilterDTO.isIgnoreCase());
  }

  @Override
  public Page<Organization> list(Criteria criteria, Pageable pageable) {
    return organizationRepository.findAll(criteria, pageable, false);
  }

  @Override
  public List<Organization> list(Criteria criteria) {
    return organizationRepository.findAll(criteria);
  }

  @Override
  public Long countOrgs(String accountIdentifier) {
    return organizationRepository.countByAccountIdentifier(accountIdentifier);
  }

  private Criteria createOrganizationFilterCriteria(Criteria criteria, OrganizationFilterDTO organizationFilterDTO) {
    if (organizationFilterDTO == null) {
      return criteria;
    }
    if (isNotBlank(organizationFilterDTO.getSearchTerm())) {
      criteria.orOperator(Criteria.where(OrganizationKeys.name).regex(organizationFilterDTO.getSearchTerm(), "i"),
          Criteria.where(OrganizationKeys.identifier).regex(organizationFilterDTO.getSearchTerm(), "i"),
          Criteria.where(OrganizationKeys.tags + "." + NGTagKeys.key).regex(organizationFilterDTO.getSearchTerm(), "i"),
          Criteria.where(OrganizationKeys.tags + "." + NGTagKeys.value)
              .regex(organizationFilterDTO.getSearchTerm(), "i"));
    }
    if (Objects.nonNull(organizationFilterDTO.getIdentifiers()) && !organizationFilterDTO.getIdentifiers().isEmpty()) {
      criteria.and(OrganizationKeys.identifier).in(organizationFilterDTO.getIdentifiers());
    }
    return criteria;
  }

  @Override
  public boolean delete(String accountIdentifier, String organizationIdentifier, Long version) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountIdentifier, OVERRIDE_ERROR)) {
      return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        Organization organization =
            organizationRepository.hardDelete(accountIdentifier, organizationIdentifier, version);

        if (isNull(organization)) {
          log.error(String.format(
              "Organization with identifier [%s] could not be deleted as it does not exist", organizationIdentifier));
          throw new EntityNotFoundException(String.format(
              "Organization with identifier [%s] does not exist in the specified scope", organizationIdentifier));
        }

        log.info(String.format("Organization with identifier [%s] was successfully deleted", organizationIdentifier));
        outboxService.save(new OrganizationDeleteEvent(accountIdentifier, OrganizationMapper.writeDto(organization)));
        instrumentationHelper.sendOrganizationDeleteEvent(organization, accountIdentifier);
        return true;
      }));
    }
  }

  @Override
  public boolean restore(String accountIdentifier, String identifier) {
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      Organization organization = organizationRepository.restore(accountIdentifier, identifier);
      boolean success = organization != null;
      if (success) {
        outboxService.save(new OrganizationRestoreEvent(accountIdentifier, OrganizationMapper.writeDto(organization)));
      }
      return success;
    }));
  }

  @Override
  public Set<String> getPermittedOrganizations(@NotNull String accountIdentifier, String orgIdentifier) {
    List<Scope> organizations;
    if (isEmpty(orgIdentifier)) {
      Criteria orgCriteria = Criteria.where(OrganizationKeys.accountIdentifier)
                                 .is(accountIdentifier)
                                 .and(OrganizationKeys.deleted)
                                 .ne(Boolean.TRUE);
      organizations = organizationRepository.findAllOrgs(orgCriteria);
    } else {
      organizations = Collections.singletonList(
          Scope.builder().accountIdentifier(accountIdentifier).orgIdentifier(orgIdentifier).build());
    }
    return scopeAccessHelper.getPermittedScopes(organizations)
        .stream()
        .map(Scope::getOrgIdentifier)
        .collect(Collectors.toSet());
  }

  private void validateUpdateOrganizationRequest(String identifier, OrganizationDTO organization) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(identifier, organization.getIdentifier())), false);
  }
}
