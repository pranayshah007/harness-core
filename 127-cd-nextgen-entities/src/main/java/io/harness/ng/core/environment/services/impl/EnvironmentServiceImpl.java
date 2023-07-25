/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.services.impl;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.exception.WingsException.USER;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.eraro.ErrorMessageConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadata;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.ng.core.environment.beans.ServiceOverridesMetadata;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.events.EnvironmentCreateEvent;
import io.harness.ng.core.events.EnvironmentDeleteEvent;
import io.harness.ng.core.events.EnvironmentForceDeleteEvent;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.events.EnvironmentUpsertEvent;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.InputSetMergeUtility;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.environment.spring.EnvironmentRepository;
import io.harness.scope.ScopeHelper;
import io.harness.setupusage.EnvironmentEntitySetupUsageHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class EnvironmentServiceImpl implements EnvironmentService {
  private final EnvironmentRepository environmentRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private final Producer eventProducer;
  private final OutboxService outboxService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Environment [%s] under Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Environment [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT = "Environment [%s] in Account [%s] already exists";
  private final InfrastructureEntityService infrastructureEntityService;
  private final ClusterService clusterService;
  private final ServiceOverrideService serviceOverrideService;
  private final ServiceOverridesServiceV2 serviceOverridesServiceV2;
  private final ServiceEntityService serviceEntityService;
  private final AccountClient accountClient;
  private final NGSettingsClient settingsClient;
  private final ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  private final EnvironmentEntitySetupUsageHelper environmentEntitySetupUsageHelper;

  @Inject
  public EnvironmentServiceImpl(EnvironmentRepository environmentRepository,
      EntitySetupUsageService entitySetupUsageService, @Named(ENTITY_CRUD) Producer eventProducer,
      OutboxService outboxService, TransactionTemplate transactionTemplate,
      InfrastructureEntityService infrastructureEntityService, ClusterService clusterService,
      ServiceOverrideService serviceOverrideService, ServiceOverridesServiceV2 serviceOverridesServiceV2,
      ServiceEntityService serviceEntityService, AccountClient accountClient, NGSettingsClient settingsClient,
      EnvironmentEntitySetupUsageHelper environmentEntitySetupUsageHelper,
      ServiceOverrideV2ValidationHelper overrideV2ValidationHelper) {
    this.environmentRepository = environmentRepository;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.infrastructureEntityService = infrastructureEntityService;
    this.clusterService = clusterService;
    this.serviceOverrideService = serviceOverrideService;
    this.serviceOverridesServiceV2 = serviceOverridesServiceV2;
    this.serviceEntityService = serviceEntityService;
    this.accountClient = accountClient;
    this.settingsClient = settingsClient;
    this.environmentEntitySetupUsageHelper = environmentEntitySetupUsageHelper;
    this.overrideV2ValidationHelper = overrideV2ValidationHelper;
  }

  @Override
  public Environment create(@NotNull @Valid Environment environment) {
    try {
      validatePresenceOfRequiredFields(environment.getAccountId(), environment.getIdentifier());
      modifyEnvironmentRequest(environment);
      Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(environment);
      Environment createdEnvironment =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            Environment tempEnvironment = environmentRepository.save(environment);
            outboxService.save(EnvironmentCreateEvent.builder()
                                   .accountIdentifier(environment.getAccountId())
                                   .orgIdentifier(environment.getOrgIdentifier())
                                   .projectIdentifier(environment.getProjectIdentifier())
                                   .environment(environment)
                                   .build());
            return tempEnvironment;
          }));
      environmentEntitySetupUsageHelper.createSetupUsages(createdEnvironment, referredEntities);
      publishEvent(environment.getAccountId(), environment.getOrgIdentifier(), environment.getProjectIdentifier(),
          environment.getIdentifier(), EventsFrameworkMetadataConstants.CREATE_ACTION);
      return createdEnvironment;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(environment.getAccountId(), environment.getOrgIdentifier(),
              environment.getProjectIdentifier(), environment.getIdentifier()),
          USER_SRE, ex);
    }
  }

  String getDuplicateServiceExistsErrorMessage(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT, serviceIdentifier, accountIdentifier);
    } else if (EmptyPredicate.isEmpty(projectIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ORG, serviceIdentifier, orgIdentifier, accountIdentifier);
    }
    return String.format(
        DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT, serviceIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
  }

  @Override
  public Optional<Environment> get(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, boolean deleted) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");

    return getEnvironmentByRef(accountId, orgIdentifier, projectIdentifier, environmentRef, deleted);
  }

  private Optional<Environment> getEnvironmentByRef(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, boolean deleted) {
    String[] envRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    if (envRefSplit == null || envRefSplit.length == 1) {
      return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
          accountId, orgIdentifier, projectIdentifier, environmentRef, !deleted);
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(environmentRef, accountId, orgIdentifier, projectIdentifier);
      return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
          envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
          envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier(), !deleted);
    }
  }

  @Override
  public Environment update(@Valid Environment requestEnvironment) {
    validatePresenceOfRequiredFields(requestEnvironment.getAccountId(), requestEnvironment.getIdentifier());
    modifyEnvironmentRequest(requestEnvironment);
    Criteria criteria = getEnvironmentEqualityCriteria(requestEnvironment, requestEnvironment.getDeleted());
    Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestEnvironment);
    Optional<Environment> environmentOptional =
        get(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
            requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(), false);
    if (environmentOptional.isPresent()) {
      Environment updatedResult =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            Environment tempResult = environmentRepository.update(criteria, requestEnvironment);
            if (tempResult == null) {
              throw new InvalidRequestException(String.format(
                  "Environment [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                  requestEnvironment.getIdentifier(), requestEnvironment.getProjectIdentifier(),
                  requestEnvironment.getOrgIdentifier()));
            }
            outboxService.save(EnvironmentUpdatedEvent.builder()
                                   .accountIdentifier(requestEnvironment.getAccountId())
                                   .orgIdentifier(requestEnvironment.getOrgIdentifier())
                                   .projectIdentifier(requestEnvironment.getProjectIdentifier())
                                   .newEnvironment(requestEnvironment)
                                   .resourceType(EnvironmentUpdatedEvent.ResourceType.ENVIRONMENT)
                                   .status(EnvironmentUpdatedEvent.Status.UPDATED)
                                   .oldEnvironment(environmentOptional.get())
                                   .build());
            return tempResult;
          }));
      environmentEntitySetupUsageHelper.updateSetupUsages(
          updatedResult, referredEntities, getReferredEntitytypes(environmentOptional.get()));
      publishEvent(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
          requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(),
          EventsFrameworkMetadataConstants.UPDATE_ACTION);
      return updatedResult;
    } else {
      throw new InvalidRequestException(String.format(
          "Environment [%s] under Project[%s], Organization [%s] doesn't exist.", requestEnvironment.getIdentifier(),
          requestEnvironment.getProjectIdentifier(), requestEnvironment.getOrgIdentifier()));
    }
  }

  @Override
  public Environment upsert(Environment requestEnvironment, UpsertOptions upsertOptions) {
    validatePresenceOfRequiredFields(requestEnvironment.getAccountId(), requestEnvironment.getIdentifier());
    modifyEnvironmentRequest(requestEnvironment);
    Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestEnvironment);
    Criteria criteria = getEnvironmentEqualityCriteria(requestEnvironment, requestEnvironment.getDeleted());
    Optional<Environment> environmentOptional =
        get(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
            requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(), false);
    Environment updatedResult = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      Environment tempResult = environmentRepository.upsert(criteria, requestEnvironment);
      if (tempResult == null) {
        throw new InvalidRequestException(String.format(
            "Environment [%s] under Project[%s], Organization [%s] couldn't be upserted or doesn't exist.",
            requestEnvironment.getIdentifier(), requestEnvironment.getProjectIdentifier(),
            requestEnvironment.getOrgIdentifier()));
      }
      if (upsertOptions.isSendOutboxEvent()) {
        outboxService.save(EnvironmentUpsertEvent.builder()
                               .accountIdentifier(requestEnvironment.getAccountId())
                               .orgIdentifier(requestEnvironment.getOrgIdentifier())
                               .projectIdentifier(requestEnvironment.getProjectIdentifier())
                               .environment(requestEnvironment)
                               .build());
      }
      return tempResult;
    }));

    if (environmentOptional.isPresent()) {
      environmentEntitySetupUsageHelper.updateSetupUsages(
          updatedResult, referredEntities, getReferredEntitytypes(environmentOptional.get()));
    } else {
      environmentEntitySetupUsageHelper.createSetupUsages(updatedResult, referredEntities);
    }
    publishEvent(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
        requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(),
        EventsFrameworkMetadataConstants.UPSERT_ACTION);
    return updatedResult;
  }

  @Override
  public Page<Environment> list(Criteria criteria, Pageable pageable) {
    return environmentRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier,
      Long version, boolean forceDelete) {
    if (forceDelete && !isForceDeleteEnabled(accountId)) {
      throw new InvalidRequestException(ErrorMessageConstants.FORCE_DELETE_SETTING_NOT_ENABLED, USER);
    }
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(environmentIdentifier), "environment Identifier must be present");

    Environment environment = Environment.builder()
                                  .accountId(accountId)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .identifier(environmentIdentifier)
                                  .version(version)
                                  .build();
    if (!forceDelete) {
      checkThatEnvironmentIsNotReferredByOthers(environment);
    }
    Criteria criteria = getEnvironmentEqualityCriteria(environment, false);

    Optional<Environment> environmentOptional =
        get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, false);
    if (environmentOptional.isPresent()) {
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        final boolean deleted = environmentRepository.delete(criteria);
        if (!deleted) {
          throw new InvalidRequestException(
              String.format("Environment [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                  environmentIdentifier, projectIdentifier, orgIdentifier));
        }
        if (forceDelete) {
          outboxService.save(EnvironmentForceDeleteEvent.builder()
                                 .accountIdentifier(accountId)
                                 .orgIdentifier(orgIdentifier)
                                 .projectIdentifier(projectIdentifier)
                                 .environment(environmentOptional.get())
                                 .build());
        } else {
          outboxService.save(EnvironmentDeleteEvent.builder()
                                 .accountIdentifier(accountId)
                                 .orgIdentifier(orgIdentifier)
                                 .projectIdentifier(projectIdentifier)
                                 .environment(environmentOptional.get())
                                 .build());
        }

        return true;
      }));
      environmentEntitySetupUsageHelper.deleteSetupUsagesWithOnlyIdentifierInfo(
          environmentIdentifier, accountId, orgIdentifier, projectIdentifier);
      publishEvent(accountId, orgIdentifier, projectIdentifier, environmentIdentifier,
          EventsFrameworkMetadataConstants.DELETE_ACTION);
      processDownstreamDeletions(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
      return true;
    } else {
      throw new InvalidRequestException(
          String.format("Environment [%s] under Project[%s], Organization [%s] doesn't exist.", environmentIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  private void processDownstreamDeletions(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    processQuietly(()
                       -> infrastructureEntityService.forceDeleteAllInEnv(
                           accountId, orgIdentifier, projectIdentifier, environmentIdentifier));
    processQuietly(
        () -> clusterService.deleteAllFromEnv(accountId, orgIdentifier, projectIdentifier, environmentIdentifier));

    boolean isOverridesV2Enabled =
        overrideV2ValidationHelper.isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);
    processQuietly(()
                       -> isOverridesV2Enabled ? (serviceOverridesServiceV2.deleteAllForEnv(
                              accountId, orgIdentifier, projectIdentifier, environmentIdentifier))
                                               : (serviceOverrideService.deleteAllInEnv(accountId, orgIdentifier,
                                                   projectIdentifier, environmentIdentifier)));
  }

  @Override
  public boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");

    return forceDeleteInternal(accountId, orgIdentifier, projectIdentifier);
  }

  private boolean forceDeleteInternal(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = getEnvironmentsEqualityCriteria(accountId, orgIdentifier, projectIdentifier);
    List<String> environments =
        environmentRepository.getEnvironmentIdentifiers(accountId, orgIdentifier, projectIdentifier);
    if (isEmpty(environments)) {
      return true;
    }
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      boolean deleted = environmentRepository.delete(criteria);
      if (deleted) {
        for (String environmentId : environments) {
          environmentEntitySetupUsageHelper.deleteSetupUsagesWithOnlyIdentifierInfo(
              environmentId, accountId, orgIdentifier, projectIdentifier);
          publishEvent(accountId, orgIdentifier, projectIdentifier, environmentId,
              EventsFrameworkMetadataConstants.DELETE_ACTION);
        }
      } else {
        throw new InvalidRequestException(getScopedErrorMessageForCascadeDeletion(orgIdentifier, projectIdentifier));
      }
      return true;
    }));
  }

  private String getScopedErrorMessageForCascadeDeletion(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(projectIdentifier)) {
      return String.format(
          "Environments under Project[%s], Organization [%s] couldn't be deleted.", projectIdentifier, orgIdentifier);
    }
    return String.format("Environments under Organization [%s] couldn't be deleted.", orgIdentifier);
  }

  @Override
  public boolean forceDeleteAllInOrg(String accountId, String orgIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgIdentifier must be present");

    return forceDeleteInternal(accountId, orgIdentifier, null);
  }

  @Override
  public List<Environment> listAccess(Criteria criteria) {
    return environmentRepository.findAllRunTimeAccess(criteria);
  }

  @Override
  public List<String> fetchesNonDeletedEnvIdentifiersFromList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefsList) {
    // assume same scope
    List<String> envIdentifierList = new ArrayList<>();
    for (String envRef : envRefsList) {
      String[] envRefSplit = StringUtils.split(envRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
      if (envRefSplit == null || envRefSplit.length == 1) {
        envIdentifierList.add(envRef);
      } else if (envRefSplit.length == 2) {
        envIdentifierList.add(envRefSplit[1]);
      }
    }
    Criteria criteria = Criteria.where(EnvironmentKeys.accountId)
                            .is(accountId)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(EnvironmentKeys.deleted)
                            .is(false)
                            .and(EnvironmentKeys.identifier)
                            .in(envIdentifierList);
    return environmentRepository.fetchesNonDeletedEnvIdentifiersFromList(criteria);
  }

  @Override
  public List<Environment> fetchesNonDeletedEnvironmentFromListOfIdentifiers(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefsList) {
    // assume same scope
    List<String> envIdentifierList = new ArrayList<>();
    for (String envRef : envRefsList) {
      String[] envRefSplit = StringUtils.split(envRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
      if (envRefSplit == null || envRefSplit.length == 1) {
        envIdentifierList.add(envRef);
      } else if (envRefSplit.length == 2) {
        envIdentifierList.add(envRefSplit[1]);
      }
    }

    Criteria criteria = Criteria.where(EnvironmentKeys.accountId)
                            .is(accountId)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(EnvironmentKeys.deleted)
                            .is(false)
                            .and(EnvironmentKeys.identifier)
                            .in(envIdentifierList);
    return environmentRepository.fetchesNonDeletedEnvironmentFromListOfIdentifiers(criteria);
  }

  @Override
  public List<Environment> fetchesNonDeletedEnvironmentFromListOfRefs(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefsList) {
    List<Environment> entities = new ArrayList<>();
    List<String> projectLevelIdentifiers = new ArrayList<>();
    List<String> orgLevelIdentifiers = new ArrayList<>();
    List<String> accountLevelIdentifiers = new ArrayList<>();

    for (String envRef : envRefsList) {
      if (isNotEmpty(envRef)) {
        IdentifierRef identifierRef =
            IdentifierRefHelper.getIdentifierRef(envRef, accountId, orgIdentifier, projectIdentifier);

        if (Scope.PROJECT.equals(identifierRef.getScope())) {
          projectLevelIdentifiers.add(identifierRef.getIdentifier());
        } else if (Scope.ORG.equals(identifierRef.getScope())) {
          orgLevelIdentifiers.add(identifierRef.getIdentifier());
        } else if (Scope.ACCOUNT.equals(identifierRef.getScope())) {
          accountLevelIdentifiers.add(identifierRef.getIdentifier());
        }
      }
    }

    if (isNotEmpty(projectLevelIdentifiers)) {
      Criteria projectCriteria = Criteria.where(EnvironmentKeys.accountId)
                                     .is(accountId)
                                     .and(EnvironmentKeys.orgIdentifier)
                                     .is(orgIdentifier)
                                     .and(EnvironmentKeys.projectIdentifier)
                                     .is(projectIdentifier)
                                     .and(EnvironmentKeys.identifier)
                                     .in(projectLevelIdentifiers);
      entities.addAll(environmentRepository.fetchesNonDeletedEnvironmentFromListOfIdentifiers(projectCriteria));
    }

    if (isNotEmpty(orgLevelIdentifiers)) {
      Criteria orgCriteria = Criteria.where(EnvironmentKeys.accountId)
                                 .is(accountId)
                                 .and(EnvironmentKeys.orgIdentifier)
                                 .is(orgIdentifier)
                                 .and(EnvironmentKeys.projectIdentifier)
                                 .is(null)
                                 .and(EnvironmentKeys.identifier)
                                 .in(orgLevelIdentifiers);
      entities.addAll(environmentRepository.fetchesNonDeletedEnvironmentFromListOfIdentifiers(orgCriteria));
    }

    if (isNotEmpty(accountLevelIdentifiers)) {
      Criteria accountCriteria = Criteria.where(EnvironmentKeys.accountId)
                                     .is(accountId)
                                     .and(EnvironmentKeys.orgIdentifier)
                                     .is(null)
                                     .and(EnvironmentKeys.projectIdentifier)
                                     .is(null)
                                     .and(EnvironmentKeys.identifier)
                                     .in(accountLevelIdentifiers);
      entities.addAll(environmentRepository.fetchesNonDeletedEnvironmentFromListOfIdentifiers(accountCriteria));
    }
    return entities;
  }

  public String getScopedErrorMessageForInvalidEnvironments(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    String errorMessage;
    if (isNotEmpty(projectIdentifier) && isNotEmpty(orgIdentifier) && isNotEmpty(accountId)) {
      errorMessage =
          String.format("Environment with identifier [%s] in project [%s], org [%s], account [%s] scope not found",
              envIdentifier, projectIdentifier, orgIdentifier, accountId);
    } else if (isNotEmpty(orgIdentifier) && isNotEmpty(accountId) && isEmpty(projectIdentifier)) {
      errorMessage = String.format("Environment with identifier [%s] in org [%s], account [%s] scope not found",
          envIdentifier, orgIdentifier, accountId);
    } else if (isNotEmpty(accountId) && isEmpty(projectIdentifier) && isEmpty(orgIdentifier)) {
      errorMessage =
          String.format("Environment with identifier [%s] in account [%s] scope not found", envIdentifier, accountId);
    } else {
      errorMessage = String.format("Environment with identifier [%s] not found", envIdentifier);
    }
    return errorMessage;
  }

  @Override
  public String createEnvironmentInputsYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    Map<String, Object> yamlInputs;
    Optional<Environment> environment = get(accountId, orgIdentifier, projectIdentifier, envIdentifier, false);
    if (environment.isPresent()) {
      if (EmptyPredicate.isEmpty(environment.get().fetchNonEmptyYaml())) {
        throw new InvalidRequestException("Environment yaml cannot be empty");
      }
      yamlInputs = createEnvironmentInputsYamlInternal(environment.get().fetchNonEmptyYaml());
    } else {
      String errorMessage =
          getScopedErrorMessageForInvalidEnvironments(accountId, orgIdentifier, projectIdentifier, envIdentifier);
      throw new NotFoundException(errorMessage);
    }
    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlPipelineUtils.writeYamlString(yamlInputs);
  }

  @Override
  public List<Map<String, String>> getAttributes(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers) {
    Map<String, List<Environment>> environments =
        fetchesNonDeletedEnvironmentFromListOfIdentifiers(accountId, orgIdentifier, projectIdentifier, envIdentifiers)
            .stream()
            .collect(groupingBy(Environment::getIdentifier));

    List<Map<String, String>> attributes = new ArrayList<>();
    for (String envId : envIdentifiers) {
      if (environments.containsKey(envId)) {
        attributes.add(ImmutableMap.of("type", environments.get(envId).get(0).getType().name()));
      } else {
        attributes.add(Collections.emptyMap());
      }
    }

    return attributes;
  }

  public Map<String, Object> createEnvironmentInputsYamlInternal(String environmentYaml) {
    Map<String, Object> yamlInputs = new HashMap<>();
    try {
      String environmentInputsYaml = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(environmentYaml);
      if (isEmpty(environmentInputsYaml)) {
        return null;
      }
      YamlField environmentYamlField =
          YamlUtils.readTree(environmentInputsYaml).getNode().getField(YamlTypes.ENVIRONMENT_YAML);
      ObjectNode environmentNode = (ObjectNode) environmentYamlField.getNode().getCurrJsonNode();
      yamlInputs.put(YamlTypes.ENVIRONMENT_INPUTS, environmentNode);
    } catch (IOException e) {
      throw new InvalidRequestException("Error occurred while creating environment inputs", e);
    }
    return yamlInputs;
  }

  private void checkThatEnvironmentIsNotReferredByOthers(Environment environment) {
    List<EntityDetail> referredByEntities;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(environment.getAccountId())
                                      .orgIdentifier(environment.getOrgIdentifier())
                                      .projectIdentifier(environment.getProjectIdentifier())
                                      .identifier(environment.getIdentifier())
                                      .build();
    try {
      Page<EntitySetupUsageDTO> entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(
          0, 10, environment.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.ENVIRONMENT, "");
      referredByEntities = entitySetupUsageDTOS.stream()
                               .map(EntitySetupUsageDTO::getReferredByEntity)
                               .collect(Collectors.toCollection(LinkedList::new));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          environment.getIdentifier(), ex);
      throw new UnexpectedException(
          "Error while deleting the Environment as was not able to check entity reference records.");
    }
    if (isNotEmpty(referredByEntities)) {
      throw new ReferencedEntityException(String.format(
          "The environment %s cannot be deleted because it is being referenced in %d %s. To delete your environment, please remove the environment references from these entities.",
          environment.getIdentifier(), referredByEntities.size(),
          referredByEntities.size() > 1 ? "entities" : "entity"));
    }
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  private void modifyEnvironmentRequest(Environment requestEnvironment) {
    if (isEmpty(requestEnvironment.getName())) {
      requestEnvironment.setName(requestEnvironment.getIdentifier());
    }
    requestEnvironment.setName(requestEnvironment.getName().trim());
    // handle empty scope identifiers as null to define the scope correctly
    requestEnvironment.setOrgIdentifier(
        EmptyPredicate.isEmpty(requestEnvironment.getOrgIdentifier()) ? null : requestEnvironment.getOrgIdentifier());
    requestEnvironment.setProjectIdentifier(EmptyPredicate.isEmpty(requestEnvironment.getProjectIdentifier())
            ? null
            : requestEnvironment.getProjectIdentifier());
  }

  private Criteria getEnvironmentEqualityCriteria(Environment requestEnvironment, boolean deleted) {
    checkArgument(isNotEmpty(requestEnvironment.getAccountId()), "accountId must be present");
    String[] envRefSplit = StringUtils.split(requestEnvironment.getIdentifier(), ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    Criteria criteria;
    if (envRefSplit == null || envRefSplit.length == 1) {
      criteria = Criteria.where(EnvironmentKeys.accountId)
                     .is(requestEnvironment.getAccountId())
                     .and(EnvironmentKeys.orgIdentifier)
                     .is(requestEnvironment.getOrgIdentifier())
                     .and(EnvironmentKeys.projectIdentifier)
                     .is(requestEnvironment.getProjectIdentifier())
                     .and(EnvironmentKeys.identifier)
                     .is(requestEnvironment.getIdentifier())
                     .and(EnvironmentKeys.deleted)
                     .is(deleted);
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(requestEnvironment.getIdentifier(), requestEnvironment.getAccountId(),
              requestEnvironment.getOrgIdentifier(), requestEnvironment.getProjectIdentifier());
      criteria = Criteria.where(EnvironmentKeys.accountId)
                     .is(envIdentifierRef.getAccountIdentifier())
                     .and(EnvironmentKeys.orgIdentifier)
                     .is(envIdentifierRef.getOrgIdentifier())
                     .and(EnvironmentKeys.projectIdentifier)
                     .is(envIdentifierRef.getProjectIdentifier())
                     .and(EnvironmentKeys.identifier)
                     .is(envIdentifierRef.getIdentifier())
                     .and(EnvironmentKeys.deleted)
                     .is(deleted);
    }

    if (requestEnvironment.getVersion() != null) {
      criteria.and(EnvironmentKeys.version).is(requestEnvironment.getVersion());
    }

    return criteria;
  }

  private Criteria getEnvironmentsEqualityCriteria(String accountId, String orgId, String projectId) {
    return Criteria.where(EnvironmentKeys.accountId)
        .is(accountId)
        .and(EnvironmentKeys.orgIdentifier)
        .is(orgId)
        .and(EnvironmentKeys.projectIdentifier)
        .is(projectId);
  }

  private void publishEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String action) {
    try {
      EntityChangeDTO.Builder environmentChangeEvent = EntityChangeDTO.newBuilder()
                                                           .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                           .setIdentifier(StringValue.of(identifier));
      if (isNotBlank(orgIdentifier)) {
        environmentChangeEvent.setOrgIdentifier(StringValue.of(orgIdentifier));
      }
      if (isNotBlank(projectIdentifier)) {
        environmentChangeEvent.setProjectIdentifier(StringValue.of(projectIdentifier));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.ENVIRONMENT_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(environmentChangeEvent.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework environment Identifier: {}", identifier, e);
    }
  }

  boolean processQuietly(BooleanSupplier b) {
    try {
      return b.getAsBoolean();
    } catch (Exception ex) {
      // ignore this
    }
    return false;
  }

  public EnvironmentInputSetYamlAndServiceOverridesMetadataDTO getEnvironmentsInputYamlAndServiceOverridesMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefs, List<String> serviceRefs,
      boolean isServiceOverrideV2FFEnabled) {
    List<EnvironmentInputSetYamlAndServiceOverridesMetadata> environmentInputSetYamlAndServiceOverridesMetadataList =
        new ArrayList<>();
    for (String envRef : envRefs) {
      // org level entities need to have compatible ids. Eg. Stage level template will call with only org.Service type
      // refs
      if (isNotEmpty(envRef) && !EngineExpressionEvaluator.hasExpressions(envRef)) {
        IdentifierRef envIdentifierRef =
            IdentifierRefHelper.getIdentifierRef(envRef, accountId, orgIdentifier, projectIdentifier);
        Optional<Environment> environment =
            environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
                envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
                envIdentifierRef.getProjectIdentifier(), envIdentifierRef.getIdentifier());
        if (environment.isPresent()) {
          boolean overridesV2Enabled =
              isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier, isServiceOverrideV2FFEnabled);

          String envInputYaml = overridesV2Enabled
              ? createEnvironmentInputYamlFromOverride(accountId, orgIdentifier, projectIdentifier, envRef)
              : createEnvironmentInputsYaml(envIdentifierRef.getAccountIdentifier(),
                  envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier(),
                  envIdentifierRef.getIdentifier());

          List<ServiceOverridesMetadata> serviceOverridesMetadataList = new ArrayList<>();
          for (String serviceRef : serviceRefs) {
            if (isNotEmpty(serviceRef) && !EngineExpressionEvaluator.hasExpressions(serviceRef)) {
              IdentifierRef serviceIdentifierRef =
                  IdentifierRefHelper.getIdentifierRef(serviceRef, accountId, orgIdentifier, projectIdentifier);

              Optional<ServiceEntity> serviceEntity = serviceEntityService.getService(
                  serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
                  serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier());
              if (serviceEntity.isPresent()) {
                // use envRef ref and service ref to fetch service overrides
                // overrides will be at same level of envRef, this can be different from service
                String serviceOverridesInputsYaml = overridesV2Enabled
                    ? serviceOverridesServiceV2.createServiceOverrideInputsYaml(
                        accountId, orgIdentifier, projectIdentifier, envRef, serviceRef)
                    : serviceOverrideService.createServiceOverrideInputsYaml(envIdentifierRef.getAccountIdentifier(),
                        envIdentifierRef.getOrgIdentifier(), envIdentifierRef.getProjectIdentifier(), envRef,
                        serviceRef);
                serviceOverridesMetadataList.add(
                    ServiceOverridesMetadata.builder()
                        .serviceRef(serviceRef)
                        .serviceOverridesYaml(serviceOverridesInputsYaml)
                        .serviceYaml(serviceEntity.get().getYaml())
                        .serviceRuntimeInputYaml(
                            serviceEntityService.createServiceInputsYaml(serviceEntity.get().getYaml(), serviceRef))
                        .build());
              }
            }
          }
          environmentInputSetYamlAndServiceOverridesMetadataList.add(
              EnvironmentInputSetYamlAndServiceOverridesMetadata.builder()
                  .envRef(envRef)
                  .envRuntimeInputYaml(envInputYaml)
                  .servicesOverrides(serviceOverridesMetadataList)
                  .envYaml(environment.get().fetchNonEmptyYaml())
                  .build());
        }
      }
    }
    return EnvironmentInputSetYamlAndServiceOverridesMetadataDTO.builder()
        .environmentsInputYamlAndServiceOverrides(environmentInputSetYamlAndServiceOverridesMetadataList)
        .build();
  }

  // envIdentifierRef : should be scoped ref
  private String createEnvironmentInputYamlFromOverride(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifierRef) {
    return serviceOverridesServiceV2.createEnvOverrideInputsYaml(
        accountId, orgIdentifier, projectIdentifier, envIdentifierRef);
  }

  @Override
  public EnvironmentInputsMergedResponseDto mergeEnvironmentInputs(
      String accountId, String orgId, String projectId, String environmentRef, String oldEnvironmentInputsYaml) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(environmentRef), "environment ref must be present");

    Optional<Environment> envEntity = get(accountId, orgId, projectId, environmentRef, false);
    if (envEntity.isEmpty()) {
      throw new NotFoundException(
          format("Environment with ref [%s] in project [%s], org [%s] not found", environmentRef, projectId, orgId));
    }

    String environmentYaml = envEntity.get().getYaml();
    if (isEmpty(environmentYaml)) {
      return EnvironmentInputsMergedResponseDto.builder().mergedEnvironmentInputsYaml("").environmentYaml("").build();
    }
    try {
      Map<String, Object> yamlInputs = createEnvironmentInputsYamlInternal(environmentYaml);

      String newEnvironmentInputsYaml =
          isEmpty(yamlInputs) ? StringUtils.EMPTY : YamlPipelineUtils.writeYamlString(yamlInputs);
      return EnvironmentInputsMergedResponseDto.builder()
          .mergedEnvironmentInputsYaml(
              InputSetMergeUtility.mergeInputs(oldEnvironmentInputsYaml, newEnvironmentInputsYaml))
          .environmentYaml(environmentYaml)
          .build();
    } catch (Exception ex) {
      throw new InvalidRequestException("Error occurred while merging old and new environment inputs", ex);
    }
  }

  private boolean isForceDeleteEnabled(String accountIdentifier) {
    return isForceDeleteFFEnabledViaSettings(accountIdentifier);
  }

  protected boolean isForceDeleteFFEnabledViaSettings(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }

  private boolean isOverridesV2Enabled(String accountId, String orgId, String projectId, boolean isOverrideV2Enabled) {
    boolean isOverrideV2SettingEnabled = false;
    Scope scope = ScopeHelper.getScope(accountId, orgId, projectId);
    if (Scope.PROJECT.equals(scope)) {
      isOverrideV2SettingEnabled =
          parseBoolean(NGRestUtils
                           .getResponse(settingsClient.getSetting(
                               SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER, accountId, orgId, projectId))
                           .getValue());

    } else if (Scope.ORG.equals(scope)) {
      isOverrideV2SettingEnabled = parseBoolean(
          NGRestUtils
              .getResponse(
                  settingsClient.getSetting(SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER, accountId, orgId, null))
              .getValue());
    } else {
      isOverrideV2SettingEnabled = parseBoolean(
          NGRestUtils
              .getResponse(
                  settingsClient.getSetting(SettingIdentifiers.SERVICE_OVERRIDE_V2_IDENTIFIER, accountId, null, null))
              .getValue());
    }
    return isOverrideV2Enabled && isOverrideV2SettingEnabled;
  }

  private Set<EntityDetailProtoDTO> getAndValidateReferredEntities(Environment environment) {
    try {
      return environmentEntitySetupUsageHelper.getAllReferredEntities(environment);
    } catch (RuntimeException ex) {
      throw new InvalidRequestException(
          String.format(
              "Exception while retrieving referred entities for environment: [%s]. ", environment.getIdentifier())
          + ex.getMessage());
    }
  }
  private Set<String> getReferredEntitytypes(Environment environment) {
    Set<EntityDetailProtoDTO> olderreferredEntities = getAndValidateReferredEntities(environment);
    Set<String> olderreferredEntityTypes = new HashSet<>();
    for (EntityDetailProtoDTO entityDetailProtoDTO : olderreferredEntities) {
      olderreferredEntityTypes.add(entityDetailProtoDTO.getType().name());
    }
    return olderreferredEntityTypes;
  }
}
