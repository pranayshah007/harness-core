/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.artifact.ArtifactUtilities.getArtifactoryRegistryUrl;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitx.GitXTransientBranchGuard;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.beans.ServiceV2YamlMetadata;
import io.harness.ng.core.dto.RepoListResponseDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.events.ServiceCreateEvent;
import io.harness.ng.core.events.ServiceDeleteEvent;
import io.harness.ng.core.events.ServiceForceDeleteEvent;
import io.harness.ng.core.events.ServiceUpdateEvent;
import io.harness.ng.core.events.ServiceUpsertEvent;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.ng.core.service.mappers.ManifestFilterHelper;
import io.harness.ng.core.service.mappers.ServiceElementMapper;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.validators.ServiceEntityValidator;
import io.harness.ng.core.service.services.validators.ServiceEntityValidatorFactory;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.template.RefreshRequestDTO;
import io.harness.ng.core.template.TemplateApplyRequestDTO;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ng.core.utils.GitXUtils;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.spec.server.ng.v1.model.ManifestsResponseDTO;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.template.yaml.TemplateRefHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class ServiceEntityServiceImpl implements ServiceEntityService {
  private static final String ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG = "accountId must be present";
  private final ServiceRepository serviceRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private final Producer eventProducer;
  private static final Integer QUERY_PAGE_SIZE = 10000;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private static final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final ServiceOverrideService serviceOverrideService;
  private final ServiceOverridesServiceV2 serviceOverridesServiceV2;
  private final ServiceEntitySetupUsageHelper entitySetupUsageHelper;
  @Inject private ServiceEntityValidatorFactory serviceEntityValidatorFactory;
  @Inject private TemplateResourceClient templateResourceClient;
  @Inject private ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  @Inject @Named("service-gitx-executor") private ExecutorService executorService;
  private final NGFeatureFlagHelperService featureFlagService;
  @Named(DEFAULT_CONNECTOR_SERVICE) private final ConnectorService connectorService;

  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Service [%s] under Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Service [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT = "Service [%s] in Account [%s] already exists";
  private static final int REMOTE_SERVICE_BATCH_SIZE = 20;

  @Inject
  public ServiceEntityServiceImpl(ServiceRepository serviceRepository, EntitySetupUsageService entitySetupUsageService,
      @Named(ENTITY_CRUD) Producer eventProducer, OutboxService outboxService, TransactionTemplate transactionTemplate,
      ServiceOverrideService serviceOverrideService, ServiceOverridesServiceV2 serviceOverridesServiceV2,
      ServiceEntitySetupUsageHelper entitySetupUsageHelper, NGFeatureFlagHelperService featureFlagService,
      @Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService) {
    this.serviceRepository = serviceRepository;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.serviceOverrideService = serviceOverrideService;
    this.serviceOverridesServiceV2 = serviceOverridesServiceV2;
    this.entitySetupUsageHelper = entitySetupUsageHelper;
    this.featureFlagService = featureFlagService;
    this.connectorService = connectorService;
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  @Override
  public ServiceEntity create(@NotNull @Valid ServiceEntity serviceEntity) {
    try {
      validatePresenceOfRequiredFields(serviceEntity.getAccountId(), serviceEntity.getIdentifier());
      setNameIfNotPresent(serviceEntity);
      modifyServiceRequest(serviceEntity);

      // cannot rely on Mongo repository throwing DuplicateKeyException since we're saving to git first
      validateIdentifierIsUnique(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(),
          serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier());

      Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(serviceEntity);
      ServiceEntityValidator serviceEntityValidator =
          serviceEntityValidatorFactory.getServiceEntityValidator(serviceEntity);
      serviceEntityValidator.validate(serviceEntity);
      ServiceEntity createdService =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            ServiceEntity service = serviceRepository.saveGitAware(serviceEntity);
            outboxService.save(getServiceCreateEvent(serviceEntity));
            return service;
          }));
      entitySetupUsageHelper.createSetupUsages(createdService, referredEntities);
      publishEvent(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(),
          serviceEntity.getIdentifier(), EventsFrameworkMetadataConstants.CREATE_ACTION);
      return createdService;
    } catch (Exception ex) {
      log.error(String.format("Error while saving service [%s]", serviceEntity.getIdentifier()), ex);
      throw new InvalidRequestException(
          String.format("Error while saving service [%s]: %s", serviceEntity.getIdentifier(), ex.getMessage()));
    }
  }

  private void validateIdentifierIsUnique(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    Optional<ServiceEntity> serviceEntity =
        serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    if (serviceEntity.isPresent()) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(accountId, orgIdentifier, projectIdentifier, serviceIdentifier),
          USER_SRE);
    }
  }

  private static ServiceCreateEvent getServiceCreateEvent(ServiceEntity serviceEntity) {
    return ServiceCreateEvent.builder()
        .accountIdentifier(serviceEntity.getAccountId())
        .orgIdentifier(serviceEntity.getOrgIdentifier())
        .projectIdentifier(serviceEntity.getProjectIdentifier())
        .service(serviceEntity)
        .build();
  }

  private Set<EntityDetailProtoDTO> getAndValidateReferredEntities(ServiceEntity serviceEntity) {
    try {
      return entitySetupUsageHelper.getAllReferredEntities(serviceEntity);
    } catch (RuntimeException ex) {
      throw new InvalidRequestException(
          String.format(
              "Exception while retrieving referred entities for service: [%s]. ", serviceEntity.getIdentifier())
          + ex.getMessage());
    }
  }

  @Override
  public Optional<ServiceEntity> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceRef, boolean deleted) {
    // default behavior to not load from cache and fallback branch
    return get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, deleted, false, false, false);
  }

  @Override
  public Optional<ServiceEntity> getMetadata(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceRef, boolean deleted) {
    // includeMetadataOnly fetches the entity from db so source code params are not needed
    return get(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, deleted, false, false, true);
  }

  @Override
  public Optional<ServiceEntity> get(String accountId, String orgIdentifier, String projectIdentifier,
      String serviceRef, boolean deleted, boolean loadFromCache, boolean loadFromFallbackBranch) {
    return get(
        accountId, orgIdentifier, projectIdentifier, serviceRef, deleted, loadFromCache, loadFromFallbackBranch, false);
  }

  private Optional<ServiceEntity> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceRef, boolean deleted, boolean loadFromCache, boolean loadFromFallbackBranch,
      boolean getMetadataOnly) {
    checkArgument(isNotEmpty(accountIdentifier), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);

    return getServiceByRef(accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, deleted, loadFromCache,
        loadFromFallbackBranch, getMetadataOnly);
  }

  private Optional<ServiceEntity> getServiceByRef(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceRef, boolean deleted, boolean loadFromCache,
      boolean loadFromFallbackBranch, boolean getMetadataOnly) {
    String[] serviceRefSplit = StringUtils.split(serviceRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    // converted to service identifier
    if (serviceRefSplit == null || serviceRefSplit.length == 1) {
      return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceRef, !deleted, loadFromCache,
          loadFromFallbackBranch, getMetadataOnly);
    } else {
      IdentifierRef serviceIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(serviceRef, accountIdentifier, orgIdentifier, projectIdentifier);
      return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
          serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
          serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier(), !deleted, loadFromCache,
          loadFromFallbackBranch, getMetadataOnly);
    }
  }

  @Override
  public ServiceEntity update(@Valid ServiceEntity requestService) {
    validatePresenceOfRequiredFields(requestService.getAccountId(), requestService.getIdentifier());
    setNameIfNotPresent(requestService);
    modifyServiceRequest(requestService);
    Criteria criteria = getServiceEqualityCriteria(requestService, requestService.getDeleted());
    Optional<ServiceEntity> serviceEntityOptional =
        get(requestService.getAccountId(), requestService.getOrgIdentifier(), requestService.getProjectIdentifier(),
            requestService.getIdentifier(), false);
    if (serviceEntityOptional.isPresent()) {
      ServiceEntity oldService = serviceEntityOptional.get();

      if (oldService.getType() != null && requestService.getType() != null
          && !oldService.getType().equals(requestService.getType())) {
        throw new InvalidRequestException("Service Deployment Type is not allowed to change.");
      }

      if (oldService.getGitOpsEnabled() != null && requestService.getGitOpsEnabled() != null
          && !oldService.getGitOpsEnabled().equals(requestService.getGitOpsEnabled())) {
        throw new InvalidRequestException("GitOps Enabled is not allowed to change.");
      }
      ServiceEntity serviceToUpdate = oldService.withYaml(requestService.getYaml())
                                          .withDescription(requestService.getDescription())
                                          .withName(requestService.getName())
                                          .withTags(requestService.getTags())
                                          .withType(requestService.getType())
                                          .withGitOpsEnabled(requestService.getGitOpsEnabled());

      // create final request service
      ServiceEntityValidator serviceEntityValidator =
          serviceEntityValidatorFactory.getServiceEntityValidator(serviceToUpdate);
      serviceEntityValidator.validate(serviceToUpdate);
      Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(serviceToUpdate);

      ServiceEntity updatedService =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            ServiceEntity updatedResult = serviceRepository.update(criteria, serviceToUpdate);
            if (updatedResult == null) {
              throw new InvalidRequestException(String.format(
                  "Service [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                  serviceToUpdate.getIdentifier(), serviceToUpdate.getProjectIdentifier(),
                  serviceToUpdate.getOrgIdentifier()));
            }
            outboxService.save(ServiceUpdateEvent.builder()
                                   .accountIdentifier(serviceToUpdate.getAccountId())
                                   .orgIdentifier(serviceToUpdate.getOrgIdentifier())
                                   .projectIdentifier(serviceToUpdate.getProjectIdentifier())
                                   .newService(updatedResult)
                                   .oldService(oldService)
                                   .build());
            return updatedResult;
          }));
      entitySetupUsageHelper.updateSetupUsages(updatedService, referredEntities);
      publishEvent(serviceToUpdate.getAccountId(), serviceToUpdate.getOrgIdentifier(),
          serviceToUpdate.getProjectIdentifier(), serviceToUpdate.getIdentifier(),
          EventsFrameworkMetadataConstants.UPDATE_ACTION);
      return updatedService;
    } else {
      throw new InvalidRequestException(String.format(
          "Service [%s] under Project[%s], Organization [%s] doesn't exist.", requestService.getIdentifier(),
          requestService.getProjectIdentifier(), requestService.getOrgIdentifier()));
    }
  }

  @Override
  public ServiceEntity upsert(@Valid ServiceEntity requestService, UpsertOptions upsertOptions) {
    validatePresenceOfRequiredFields(requestService.getAccountId(), requestService.getIdentifier());
    setNameIfNotPresent(requestService);
    modifyServiceRequest(requestService);
    Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestService);

    Criteria criteria = getServiceEqualityCriteria(requestService, requestService.getDeleted());

    Optional<ServiceEntity> serviceEntityOptional =
        get(requestService.getAccountId(), requestService.getOrgIdentifier(), requestService.getProjectIdentifier(),
            requestService.getIdentifier(), false);

    serviceEntityOptional.ifPresent(oldService -> {
      if (oldService.getType() != null && requestService.getType() != null
          && !oldService.getType().equals(requestService.getType())) {
        throw new InvalidRequestException("Service Deployment Type is not allowed to change.");
      }

      if (oldService.getGitOpsEnabled() != null && requestService.getGitOpsEnabled() != null
          && !oldService.getGitOpsEnabled().equals(requestService.getGitOpsEnabled())) {
        throw new InvalidRequestException("GitOps Enabled is not allowed to change.");
      }
    });

    ServiceEntityValidator serviceEntityValidator =
        serviceEntityValidatorFactory.getServiceEntityValidator(requestService);
    serviceEntityValidator.validate(requestService);
    ServiceEntity upsertedService =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          ServiceEntity result = serviceRepository.upsert(criteria, requestService);
          if (result == null) {
            throw new InvalidRequestException(
                String.format("Service [%s] under Project[%s], Organization [%s] couldn't be upserted.",
                    requestService.getIdentifier(), requestService.getProjectIdentifier(),
                    requestService.getOrgIdentifier()));
          }
          if (upsertOptions.isSendOutboxEvent()) {
            outboxService.save(ServiceUpsertEvent.builder()
                                   .accountIdentifier(requestService.getAccountId())
                                   .orgIdentifier(requestService.getOrgIdentifier())
                                   .projectIdentifier(requestService.getProjectIdentifier())
                                   .oldService(serviceEntityOptional.orElse(null))
                                   .service(requestService)
                                   .build());
          }
          return result;
        }));
    if (upsertOptions.isPublishSetupUsages()) {
      entitySetupUsageHelper.updateSetupUsages(upsertedService, referredEntities);
    }
    publishEvent(requestService.getAccountId(), requestService.getOrgIdentifier(),
        requestService.getProjectIdentifier(), requestService.getIdentifier(),
        EventsFrameworkMetadataConstants.UPSERT_ACTION);
    return upsertedService;
  }

  @Override
  public Page<ServiceEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable) {
    return serviceRepository.findAll(criteria, pageable);
  }

  @Override
  public List<ServiceEntity> listRunTimePermission(Criteria criteria) {
    int NO_LIMIT = 50000;
    return serviceRepository.findAll(criteria, Pageable.ofSize(NO_LIMIT)).toList();
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String serviceRef,
      Long version, boolean forceDelete) {
    checkArgument(isNotEmpty(accountId), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);
    checkArgument(isNotEmpty(serviceRef), "serviceRef must be present");

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(serviceRef)
                                      .version(version)
                                      .build();
    if (!forceDelete) {
      checkThatServiceIsNotReferredByOthers(serviceEntity);
    }
    Criteria criteria = getServiceEqualityCriteria(serviceEntity, false);

    Optional<ServiceEntity> serviceEntityOptional =
        getMetadata(accountId, orgIdentifier, projectIdentifier, serviceRef, false);

    if (serviceEntityOptional.isPresent()) {
      boolean success = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ServiceEntity serviceEntityRetrieved = serviceEntityOptional.get();
        final boolean deleted = serviceRepository.delete(criteria);
        if (!deleted) {
          throw new InvalidRequestException(
              String.format("Service [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                  serviceEntity.getIdentifier(), projectIdentifier, orgIdentifier));
        }
        if (forceDelete) {
          outboxService.save(new ServiceForceDeleteEvent(accountId, serviceEntityRetrieved.getOrgIdentifier(),
              serviceEntityRetrieved.getProjectIdentifier(), serviceEntityRetrieved));
        } else {
          outboxService.save(new ServiceDeleteEvent(accountId, serviceEntityRetrieved.getOrgIdentifier(),
              serviceEntityRetrieved.getProjectIdentifier(), serviceEntityRetrieved));
        }
        return true;
      }));
      boolean isOverridesV2Enabled =
          overrideV2ValidationHelper.isOverridesV2Enabled(accountId, orgIdentifier, projectIdentifier);
      processQuietly(()
                         -> isOverridesV2Enabled
              ? (serviceOverridesServiceV2.deleteAllOfService(accountId, orgIdentifier, projectIdentifier, serviceRef))
              : (serviceOverrideService.deleteAllInProjectForAService(
                  accountId, orgIdentifier, projectIdentifier, serviceRef)));
      entitySetupUsageHelper.deleteSetupUsages(serviceEntityOptional.get());
      publishEvent(
          accountId, orgIdentifier, projectIdentifier, serviceRef, EventsFrameworkMetadataConstants.DELETE_ACTION);
      return success;
    } else {
      throw new InvalidRequestException(
          String.format("Service [%s] under Project[%s], Organization [%s] doesn't exist.", serviceRef,
              projectIdentifier, orgIdentifier));
    }
  }

  private void publishEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String action) {
    try {
      EntityChangeDTO.Builder serviceChangeEvent = EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                       .setIdentifier(StringValue.of(identifier));
      if (isNotBlank(orgIdentifier)) {
        serviceChangeEvent.setOrgIdentifier(StringValue.of(orgIdentifier));
      }
      if (isNotBlank(projectIdentifier)) {
        serviceChangeEvent.setProjectIdentifier(StringValue.of(projectIdentifier));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(Map.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                  EventsFrameworkMetadataConstants.SERVICE_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(serviceChangeEvent.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework service Identifier: {}", identifier, e);
    }
  }

  private void checkThatServiceIsNotReferredByOthers(ServiceEntity serviceEntity) {
    // check handling here
    List<EntityDetail> referredByEntities;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(serviceEntity.getAccountId())
                                      .orgIdentifier(serviceEntity.getOrgIdentifier())
                                      .projectIdentifier(serviceEntity.getProjectIdentifier())
                                      .identifier(serviceEntity.getIdentifier())
                                      .build();
    try {
      Page<EntitySetupUsageDTO> entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(
          0, 10, serviceEntity.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.SERVICE, "");
      referredByEntities = entitySetupUsageDTOS.stream()
                               .map(EntitySetupUsageDTO::getReferredByEntity)
                               .collect(Collectors.toCollection(LinkedList::new));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          serviceEntity.getIdentifier(), ex);
      throw new UnexpectedException(
          "Error while deleting the Service as was not able to check entity reference records.");
    }
    if (isNotEmpty(referredByEntities)) {
      throw new ReferencedEntityException(String.format(
          "The service %s cannot be deleted because it is being referenced in %d %s. To delete your service, please remove the reference service from these entities.",
          serviceEntity.getIdentifier(), referredByEntities.size(),
          referredByEntities.size() > 1 ? "entities" : "entity"));
    }
  }

  private void setNameIfNotPresent(ServiceEntity requestService) {
    if (isEmpty(requestService.getName())) {
      requestService.setName(requestService.getIdentifier());
    }
  }

  private Criteria getServiceEqualityCriteria(ServiceEntity requestService, boolean deleted) {
    checkArgument(isNotEmpty(requestService.getAccountId()), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);
    String[] serviceRefSplit = StringUtils.split(requestService.getIdentifier(), ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
    Criteria criteria;
    if (serviceRefSplit == null || serviceRefSplit.length == 1) {
      criteria = Criteria.where(ServiceEntityKeys.accountId)
                     .is(requestService.getAccountId())
                     .and(ServiceEntityKeys.orgIdentifier)
                     .is(requestService.getOrgIdentifier())
                     .and(ServiceEntityKeys.projectIdentifier)
                     .is(requestService.getProjectIdentifier())
                     .and(ServiceEntityKeys.identifier)
                     .is(requestService.getIdentifier())
                     .and(ServiceEntityKeys.deleted)
                     .is(deleted);
    } else {
      IdentifierRef serviceIdentifierRef = IdentifierRefHelper.getIdentifierRef(requestService.getIdentifier(),
          requestService.getAccountId(), requestService.getOrgIdentifier(), requestService.getProjectIdentifier());
      criteria = Criteria.where(ServiceEntityKeys.accountId)
                     .is(serviceIdentifierRef.getAccountIdentifier())
                     .and(ServiceEntityKeys.orgIdentifier)
                     .is(serviceIdentifierRef.getOrgIdentifier())
                     .and(ServiceEntityKeys.projectIdentifier)
                     .is(serviceIdentifierRef.getProjectIdentifier())
                     .and(ServiceEntityKeys.identifier)
                     .is(serviceIdentifierRef.getIdentifier())
                     .and(ServiceEntityKeys.deleted)
                     .is(deleted);
    }

    if (requestService.getVersion() != null) {
      criteria.and(ServiceEntityKeys.version).is(requestService.getVersion());
    }
    return criteria;
  }

  @Override
  public Page<ServiceEntity> bulkCreate(String accountId, @NotNull List<ServiceEntity> serviceEntities) {
    try {
      validateTheServicesList(serviceEntities);
      populateDefaultNameIfNotPresent(serviceEntities);
      modifyServiceRequestBatch(serviceEntities);
      List<Set<EntityDetailProtoDTO>> referredEntityList = new ArrayList<>();
      for (ServiceEntity serviceEntity : serviceEntities) {
        referredEntityList.add(getAndValidateReferredEntities(serviceEntity));
      }
      List<ServiceEntity> outputServiceEntitiesList = (List<ServiceEntity>) serviceRepository.saveAll(serviceEntities);
      int i = 0;
      for (ServiceEntity serviceEntity : serviceEntities) {
        publishEvent(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(),
            serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier(),
            EventsFrameworkMetadataConstants.CREATE_ACTION);
        outboxService.save(getServiceCreateEvent(serviceEntity));
        entitySetupUsageHelper.createSetupUsages(serviceEntity, referredEntityList.get(i));
        i++;
      }
      return new PageImpl<>(outputServiceEntitiesList);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(accountId, ex.getMessage()), USER_SRE, ex);
    } catch (WingsException ex) {
      String serviceNames = serviceEntities.stream().map(ServiceEntity::getName).collect(Collectors.joining(","));
      log.info(
          "Encountered exception while saving the service entity records of [{}], with exception", serviceNames, ex);
      throw new InvalidRequestException(
          "Encountered exception while saving the service entity records. " + ex.getMessage());
    } catch (Exception ex) {
      String serviceNames = serviceEntities.stream().map(ServiceEntity::getName).collect(Collectors.joining(","));
      log.info(
          "Encountered exception while saving the service entity records of [{}], with exception", serviceNames, ex);
      throw new UnexpectedException("Encountered exception while saving the service entity records.");
    }
  }

  @Override
  public List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getAllServices(
        accountIdentifier, orgIdentifier, projectIdentifier, QUERY_PAGE_SIZE, true, new ArrayList<>());
  }

  @Override
  public List<ServiceEntity> getAllNonDeletedServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> sort) {
    return getAllServices(accountIdentifier, orgIdentifier, projectIdentifier, QUERY_PAGE_SIZE, false, sort);
  }

  @Override
  public List<ServiceEntity> getMetadata(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceRefs) {
    if (isEmpty(serviceRefs)) {
      return emptyList();
    }
    return getScopedServiceEntities(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);
  }

  @Override
  public List<ServiceV2YamlMetadata> getServicesYamlMetadata(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> serviceRefs, Map<String, String> servicesMetadataWithGitInfo,
      boolean loadFromCache) {
    if (isEmpty(serviceRefs)) {
      return emptyList();
    }

    List<ServiceV2YamlMetadata> servicesYamlMetadata = null;

    try {
      servicesYamlMetadata = getServicesYamlMetadataInternal(
          accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs, servicesMetadataWithGitInfo, loadFromCache);
    } catch (CompletionException ex) {
      // internal method always wraps the CompletionException, so we will have a cause
      log.error(String.format("Error while getting services: %s", serviceRefs), ex);
      Throwables.throwIfUnchecked(ex.getCause());
    } catch (Exception ex) {
      log.error(String.format("Unexpected error occurred while getting services: %s", serviceRefs), ex);
      throw new InternalServerErrorException(
          String.format("Unexpected error occurred while getting services: %s: [%s]", serviceRefs, ex.getMessage()),
          ex);
    }

    return servicesYamlMetadata;
  }

  private ServiceV2YamlMetadata createServiceV2YamlMetadata(ServiceEntity serviceEntity) {
    if (featureFlagService.isEnabled(
            serviceEntity.getAccountId(), FeatureName.CDS_ARTIFACTORY_REPOSITORY_URL_MANDATORY)) {
      serviceEntity = updateArtifactoryRegistryUrlIfEmpty(serviceEntity, serviceEntity.getAccountId(),
          serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier());
    }

    if (isBlank(serviceEntity.getYaml())) {
      log.info("Service with identifier {} is not configured with a Service definition. Service Yaml is empty",
          serviceEntity.getIdentifier());
      return ServiceV2YamlMetadata.builder()
          .serviceIdentifier(serviceEntity.getIdentifier())
          .serviceYaml("")
          .inputSetTemplateYaml("")
          .projectIdentifier(serviceEntity.getProjectIdentifier())
          .orgIdentifier(serviceEntity.getOrgIdentifier())
          .build();
    }

    final String serviceInputSetYaml = createServiceInputsYaml(serviceEntity.getYaml(), serviceEntity.getIdentifier());
    return ServiceV2YamlMetadata.builder()
        .serviceIdentifier(serviceEntity.getIdentifier())
        .serviceYaml(serviceEntity.getYaml())
        .inputSetTemplateYaml(serviceInputSetYaml)
        .orgIdentifier(serviceEntity.getOrgIdentifier())
        .projectIdentifier(serviceEntity.getProjectIdentifier())
        .connectorRef(serviceEntity.getConnectorRef())
        .storeType(serviceEntity.getStoreType())
        .fallbackBranch(serviceEntity.getFallBackBranch())
        .entityGitDetails(ServiceElementMapper.getEntityGitDetails(serviceEntity))
        .build();
  }

  private YamlNode validateAndGetYamlNode(String yaml) {
    if (isEmpty(yaml)) {
      throw new InvalidRequestException("Service YAML is empty.");
    }
    YamlNode yamlNode = null;
    try {
      yamlNode = YamlUtils.readTree(yaml).getNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
    }
    return yamlNode;
  }

  @Override
  public ServiceEntity updateArtifactoryRegistryUrlIfEmpty(
      ServiceEntity serviceEntity, String accountId, String orgIdentifier, String projectIdentifier) {
    if (serviceEntity == null) {
      return null;
    }

    String repositoryUrlField = "repositoryUrl";

    String serviceYaml = serviceEntity.getYaml();

    YamlNode node = validateAndGetYamlNode(serviceYaml);

    JsonNode artifactSpecNode = null;
    if (node != null) {
      JsonNode serviceNode = node.getCurrJsonNode().get("service");

      if (serviceNode != null) {
        JsonNode serviceDefinitionNode = serviceNode.get("serviceDefinition");

        if (serviceDefinitionNode != null) {
          JsonNode specNode = serviceDefinitionNode.get("spec");

          if (specNode != null) {
            JsonNode artifactsNode = specNode.get("artifacts");

            if (artifactsNode != null) {
              JsonNode primaryNode = artifactsNode.get("primary");

              if (primaryNode != null) {
                artifactSpecNode = primaryNode.get("sources");
              }
            }
          }
        }
      }
    }

    if (artifactSpecNode == null) {
      return serviceEntity;
    }

    Map<String, Object> yamlResMap = getResMap(node, null);
    LinkedHashMap<String, Object> serviceResMap = (LinkedHashMap<String, Object>) yamlResMap.get("service");
    LinkedHashMap<String, Object> serviceDefinitionResMap =
        (LinkedHashMap<String, Object>) serviceResMap.get("serviceDefinition");
    LinkedHashMap<String, Object> specResMap = (LinkedHashMap<String, Object>) serviceDefinitionResMap.get("spec");
    LinkedHashMap<String, Object> artifactsResMap = (LinkedHashMap<String, Object>) specResMap.get("artifacts");
    LinkedHashMap<String, Object> primaryResMap = (LinkedHashMap<String, Object>) artifactsResMap.get("primary");
    ArrayList<LinkedHashMap<String, Object>> sourcesResMap =
        (ArrayList<LinkedHashMap<String, Object>>) primaryResMap.get("sources");

    for (int i = 0; i < sourcesResMap.size(); i++) {
      LinkedHashMap<String, Object> source = sourcesResMap.get(i);

      String type = String.valueOf(source.get("type"));
      type = type.substring(1, type.length() - 1);
      LinkedHashMap<String, Object> spec = (LinkedHashMap<String, Object>) source.get("spec");

      if (type.equals(ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME)) {
        if (!spec.containsKey(repositoryUrlField)) {
          String finalUrl = null;
          String connectorRef = String.valueOf(spec.get("connectorRef"));
          connectorRef = connectorRef.substring(1, connectorRef.length() - 1);
          String repository = String.valueOf(spec.get("repository"));
          repository = repository.substring(1, repository.length() - 1);
          String repositoryFormat = String.valueOf(spec.get("repositoryFormat"));
          repositoryFormat = repositoryFormat.substring(1, repositoryFormat.length() - 1);

          if (repositoryFormat.equals("docker")) {
            IdentifierRef connectorIdentifier =
                IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
            ArtifactoryConnectorDTO connector = getConnector(connectorIdentifier);
            finalUrl = getArtifactoryRegistryUrl(connector.getArtifactoryServerUrl(), null, repository);

            spec.put(repositoryUrlField, finalUrl);
          }
        }
        source.replace("spec", spec);
      }
      sourcesResMap.set(i, source);
    }

    primaryResMap.replace("sources", sourcesResMap);
    artifactsResMap.replace("primary", primaryResMap);
    specResMap.replace("artifacts", artifactsResMap);
    serviceDefinitionResMap.replace("spec", specResMap);
    serviceResMap.replace("serviceDefinition", serviceDefinitionResMap);
    yamlResMap.replace("service", serviceResMap);

    serviceEntity.setYaml(YamlPipelineUtils.writeYamlString(yamlResMap));
    return serviceEntity;
  }

  private ArtifactoryConnectorDTO getConnector(IdentifierRef artifactoryConnectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(artifactoryConnectorRef.getAccountIdentifier(), artifactoryConnectorRef.getOrgIdentifier(),
            artifactoryConnectorRef.getProjectIdentifier(), artifactoryConnectorRef.getIdentifier());

    if (connectorDTO.isEmpty() || !isAArtifactoryConnector(connectorDTO.get())) {
      throw new ArtifactoryRegistryException(String.format("Connector not found for identifier : [%s] with scope: [%s]",
          artifactoryConnectorRef.getIdentifier(), artifactoryConnectorRef.getScope()));
    }
    ConnectorInfoDTO connectors = connectorDTO.get().getConnector();
    return (ArtifactoryConnectorDTO) connectors.getConnectorConfig();
  }

  private static boolean isAArtifactoryConnector(@NotNull ConnectorResponseDTO connectorResponseDTO) {
    return ConnectorType.ARTIFACTORY == (connectorResponseDTO.getConnector().getConnectorType());
  }

  private Map<String, Object> getResMap(YamlNode yamlNode, String url) {
    Map<String, Object> resMap = new LinkedHashMap<>();
    List<YamlField> childFields = yamlNode.fields();
    boolean connectorRefFlag = false;
    // Iterating over the YAML
    for (YamlField childYamlField : childFields) {
      String fieldName = childYamlField.getName();
      if (fieldName.equals("connectorRef")) {
        connectorRefFlag = true;
      }
      JsonNode value = childYamlField.getNode().getCurrJsonNode();
      if (value.isValueNode() || YamlUtils.checkIfNodeIsArrayWithPrimitiveTypes(value)) {
        // Value -> ValueNode
        resMap.put(fieldName, value);
      } else if (value.isArray()) {
        // Value -> ArrayNode
        resMap.put(fieldName, getResMapInArray(childYamlField.getNode(), url));
      } else {
        // Value -> ObjectNode
        resMap.put(fieldName, getResMap(childYamlField.getNode(), url));
      }
    }
    if (connectorRefFlag && EmptyPredicate.isNotEmpty(url)) {
      resMap.put("repositoryUrl", url);
    }
    return resMap;
  }

  // Gets the ResMap if the yamlNode is of the type Array
  private List<Object> getResMapInArray(YamlNode yamlNode, String url) {
    List<Object> arrayList = new ArrayList<>();
    // Iterate over the array
    for (YamlNode arrayElement : yamlNode.asArray()) {
      if (yamlNode.getCurrJsonNode().isValueNode()) {
        // Value -> LeafNode
        arrayList.add(arrayElement);
      } else if (arrayElement.isArray()) {
        // Value -> Array
        arrayList.add(getResMapInArray(arrayElement, url));
      } else {
        // Value -> Object
        arrayList.add(getResMap(arrayElement, url));
      }
    }
    return arrayList;
  }

  private List<ServiceV2YamlMetadata> getServicesYamlMetadataInternal(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> serviceRefs, Map<String, String> servicesMetadataWithGitInfo,
      boolean loadFromCache) {
    List<ServiceV2YamlMetadata> yamlMetadata = new ArrayList<>();

    // Get all service entities
    List<ServiceEntity> serviceEntities =
        getScopedServiceEntities(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs);

    for (int i = 0; i < serviceEntities.size(); i += REMOTE_SERVICE_BATCH_SIZE) {
      List<ServiceEntity> batch = getBatch(serviceEntities, i);

      List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

      for (ServiceEntity serviceEntity : batch) {
        if (StoreType.REMOTE.equals(serviceEntity.getStoreType())) {
          String serviceRef = IdentifierRefHelper.getRefFromIdentifierOrRef(serviceEntity.getAccountId(),
              serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier());
          String branchInfo = servicesMetadataWithGitInfo.get(serviceRef);

          CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try (GitXTransientBranchGuard ignore = new GitXTransientBranchGuard(branchInfo)) {
              serviceRepository.getRemoteServiceWithYaml(serviceEntity, loadFromCache, false);
            }
          }, executorService);

          batchFutures.add(future);
        }
      }

      // Wait for the batch to complete
      CompletableFuture<Void> allOf = CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]));
      allOf.join();
    }

    for (ServiceEntity entity : serviceEntities) {
      yamlMetadata.add(createServiceV2YamlMetadata(entity));
    }

    return yamlMetadata;
  }

  private static List<ServiceEntity> getBatch(List<ServiceEntity> remoteServiceEntities, int i) {
    int endIndex = Math.min(i + REMOTE_SERVICE_BATCH_SIZE, remoteServiceEntities.size());
    return remoteServiceEntities.subList(i, endIndex);
  }

  private List<ServiceEntity> getScopedServiceEntities(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceRefs) {
    List<ServiceEntity> entities = new ArrayList<>();
    List<String> projectLevelIdentifiers = new ArrayList<>();
    List<String> orgLevelIdentifiers = new ArrayList<>();
    List<String> accountLevelIdentifiers = new ArrayList<>();

    ServiceFilterHelper.populateIdentifiersOfEachLevel(accountIdentifier, orgIdentifier, projectIdentifier, serviceRefs,
        projectLevelIdentifiers, orgLevelIdentifiers, accountLevelIdentifiers);

    if (isNotEmpty(projectLevelIdentifiers)) {
      Criteria projectCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                     .is(accountIdentifier)
                                     .and(ServiceEntityKeys.orgIdentifier)
                                     .is(orgIdentifier)
                                     .and(ServiceEntityKeys.projectIdentifier)
                                     .is(projectIdentifier)
                                     .and(ServiceEntityKeys.identifier)
                                     .in(projectLevelIdentifiers);
      entities.addAll(serviceRepository.findAll(projectCriteria));
    }

    if (isNotEmpty(orgLevelIdentifiers)) {
      Criteria orgCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                 .is(accountIdentifier)
                                 .and(ServiceEntityKeys.orgIdentifier)
                                 .is(orgIdentifier)
                                 .and(ServiceEntityKeys.projectIdentifier)
                                 .is(null)
                                 .and(ServiceEntityKeys.identifier)
                                 .in(orgLevelIdentifiers);
      entities.addAll(serviceRepository.findAll(orgCriteria));
    }

    if (isNotEmpty(accountLevelIdentifiers)) {
      Criteria accountCriteria = Criteria.where(ServiceEntityKeys.accountId)
                                     .is(accountIdentifier)
                                     .and(ServiceEntityKeys.orgIdentifier)
                                     .is(null)
                                     .and(ServiceEntityKeys.projectIdentifier)
                                     .is(null)
                                     .and(ServiceEntityKeys.identifier)
                                     .in(accountLevelIdentifiers);
      entities.addAll(serviceRepository.findAll(accountCriteria));
    }
    return entities;
  }

  @Override
  public boolean isServiceField(String fieldName, JsonNode serviceValue) {
    return YamlTypes.SERVICE_ENTITY.equals(fieldName) && serviceValue.isObject()
        && (serviceValue.get(YamlTypes.SERVICE_REF) != null || serviceValue.get(YamlTypes.USE_FROM_STAGE) != null);
  }

  @Override
  public Integer findActiveServicesCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs) {
    if (timestampInMs <= 0) {
      throw new InvalidRequestException("Invalid timestamp while fetching active services count : " + timestampInMs);
    }
    return serviceRepository
        .findActiveServiceCountAtGivenTimestamp(accountIdentifier, orgIdentifier, projectIdentifier, timestampInMs)
        .intValue();
  }

  @Override
  public String createServiceInputsYaml(String yaml, String serviceIdentifier) {
    return createServiceInputsYamlInternal(yaml, serviceIdentifier, null);
  }

  @Override
  public String createServiceInputsYamlGivenPrimaryArtifactRef(
      String serviceYaml, String serviceIdentifier, String primaryArtifactRef) {
    return createServiceInputsYamlInternal(serviceYaml, serviceIdentifier, primaryArtifactRef);
  }

  private String createServiceInputsYamlInternal(
      String serviceYaml, String serviceIdentifier, String primaryArtifactRef) {
    Map<String, Object> serviceInputs = new HashMap<>();

    try {
      YamlField serviceYamlField = YamlUtils.readTree(serviceYaml).getNode().getField(YamlTypes.SERVICE_ENTITY);
      if (serviceYamlField == null) {
        throw new YamlException(
            String.format("Yaml provided for service %s does not have service root field.", serviceIdentifier));
      }

      modifyServiceDefinitionNodeBeforeCreatingServiceInputs(serviceYamlField, serviceIdentifier, primaryArtifactRef);
      ObjectNode serviceNode = (ObjectNode) serviceYamlField.getNode().getCurrJsonNode();
      ObjectNode serviceDefinitionNode = serviceNode.retain(YamlTypes.SERVICE_DEFINITION);
      if (EmptyPredicate.isEmpty(serviceDefinitionNode)) {
        return null;
      }
      String serviceDefinition = serviceDefinitionNode.toString();
      String serviceDefinitionInputs =
          RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(serviceDefinition);

      if (isEmpty(serviceDefinitionInputs)) {
        return serviceDefinitionInputs;
      }
      JsonNode serviceDefinitionInputNode = YamlUtils.readTree(serviceDefinitionInputs).getNode().getCurrJsonNode();
      serviceInputs.put(YamlTypes.SERVICE_INPUTS, serviceDefinitionInputNode);
      return YamlPipelineUtils.writeYamlString(serviceInputs);
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Error occurred while creating service inputs for service %s", serviceIdentifier), e);
    }
  }

  @Override
  public ArtifactSourcesResponseDTO getArtifactSourceInputs(String yaml, String serviceIdentifier) {
    try {
      YamlField serviceYamlField = YamlUtils.readTree(yaml).getNode().getField(YamlTypes.SERVICE_ENTITY);
      if (serviceYamlField == null) {
        throw new YamlException(
            String.format("Yaml provided for service %s does not have service root field.", serviceIdentifier));
      }

      YamlField primaryArtifactField = ServiceFilterHelper.getPrimaryArtifactNodeFromServiceYaml(serviceYamlField);
      if (primaryArtifactField == null) {
        return ArtifactSourcesResponseDTO.builder().build();
      }

      YamlField artifactSourcesField = primaryArtifactField.getNode().getField(YamlTypes.ARTIFACT_SOURCES);
      if (artifactSourcesField == null) {
        return ArtifactSourcesResponseDTO.builder().build();
      }
      List<String> artifactSourceIdentifiers = new ArrayList<>();
      Map<String, String> sourceIdentifierToSourceInputMap = new HashMap<>();

      List<YamlNode> artifactSources = artifactSourcesField.getNode().asArray();
      for (YamlNode artifactSource : artifactSources) {
        String sourceIdentifier = artifactSource.getIdentifier();
        artifactSourceIdentifiers.add(sourceIdentifier);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode tempSourceNode = objectMapper.createObjectNode();
        // Adding root node because RuntimeInputFormHelper.createTemplateFromYaml() method requires root node.
        tempSourceNode.set(YamlTypes.ARTIFACT_SOURCES, artifactSource.getCurrJsonNode());
        String runtimeInputFormWithSourcesRootNode =
            RuntimeInputFormHelper.createTemplateFromYaml(tempSourceNode.toString());
        if (EmptyPredicate.isNotEmpty(runtimeInputFormWithSourcesRootNode)) {
          JsonNode runtimeInputFormNode =
              YamlUtils.readTree(runtimeInputFormWithSourcesRootNode).getNode().getCurrJsonNode();
          sourceIdentifierToSourceInputMap.put(sourceIdentifier,
              YamlPipelineUtils.writeYamlString(runtimeInputFormNode.get(YamlTypes.ARTIFACT_SOURCES)));
        }
      }

      return ArtifactSourcesResponseDTO.builder()
          .sourceIdentifiers(artifactSourceIdentifiers)
          .sourceIdentifierToSourceInputMap(sourceIdentifierToSourceInputMap)
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Error occurred while creating service inputs for service %s", serviceIdentifier), e);
    }
  }

  @Override
  public ServiceInputsMergedResponseDto mergeServiceInputs(
      String accountId, String orgId, String projectId, String serviceRef, String oldServiceInputsYaml) {
    checkArgument(isNotEmpty(accountId), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);
    checkArgument(isNotEmpty(serviceRef), "serviceRef must be present");
    Optional<ServiceEntity> serviceEntity = get(accountId, orgId, projectId, serviceRef, false);
    if (serviceEntity.isEmpty()) {
      throw new NotFoundException(
          format("Service with ref [%s] in project [%s], org [%s] not found", serviceRef, projectId, orgId));
    }

    String serviceYaml = serviceEntity.get().getYaml();
    if (isEmpty(serviceYaml)) {
      return ServiceInputsMergedResponseDto.builder().mergedServiceInputsYaml("").serviceYaml("").build();
    }
    try {
      YamlNode primaryArtifactRefNode = null;
      if (isNotEmpty(oldServiceInputsYaml)) {
        YamlNode oldServiceInputsNode = YamlUtils.readTree(oldServiceInputsYaml).getNode();
        primaryArtifactRefNode = YamlNodeUtils.goToPathUsingFqn(
            oldServiceInputsNode, "serviceInputs.serviceDefinition.spec.artifacts.primary.primaryArtifactRef");
      }

      String newServiceInputsYaml = createServiceInputsYamlGivenPrimaryArtifactRef(
          serviceYaml, serviceRef, primaryArtifactRefNode == null ? null : primaryArtifactRefNode.asText());
      return ServiceInputsMergedResponseDto.builder()
          .mergedServiceInputsYaml(InputSetMergeUtility.mergeInputs(oldServiceInputsYaml, newServiceInputsYaml))
          .serviceYaml(serviceYaml)
          .build();
    } catch (IOException ex) {
      throw new InvalidRequestException("Error occurred while merging old and new service inputs", ex);
    }
  }

  private void modifyServiceDefinitionNodeBeforeCreatingServiceInputs(
      YamlField serviceYamlField, String serviceIdentifier, String primaryArtifactRef) {
    YamlField primaryArtifactField = ServiceFilterHelper.getPrimaryArtifactNodeFromServiceYaml(serviceYamlField);
    if (primaryArtifactField == null) {
      return;
    }
    if (!primaryArtifactField.getNode().isObject()) {
      throw new InvalidRequestException(
          String.format("Primary field inside service %s should be an OBJECT node but was %s", serviceIdentifier,
              primaryArtifactField.getNode().getCurrJsonNode().getNodeType()));
    }

    YamlField primaryArtifactRefField = primaryArtifactField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT_REF);
    YamlField artifactSourcesField = primaryArtifactField.getNode().getField(YamlTypes.ARTIFACT_SOURCES);
    if (primaryArtifactRefField == null || artifactSourcesField == null) {
      return;
    }

    if (!artifactSourcesField.getNode().isArray()) {
      throw new InvalidRequestException(
          String.format("Artifact sources inside service %s should be ARRAY node but was %s", serviceIdentifier,
              artifactSourcesField.getNode().getCurrJsonNode().getNodeType()));
    }

    String primaryArtifactRefValue = primaryArtifactRefField.getNode().asText();

    ObjectNode primaryArtifactObjectNode = (ObjectNode) primaryArtifactField.getNode().getCurrJsonNode();
    if (NGExpressionUtils.matchesInputSetPattern(primaryArtifactRefValue)) {
      if (EmptyPredicate.isNotEmpty(primaryArtifactRef)
          && !NGExpressionUtils.matchesInputSetPattern(primaryArtifactRef)) {
        primaryArtifactRefValue = primaryArtifactRef;
      } else {
        primaryArtifactObjectNode.remove(YamlTypes.ARTIFACT_SOURCES);
        primaryArtifactObjectNode.put(YamlTypes.ARTIFACT_SOURCES, "<+input>");
        return;
      }
    }

    // either reference in runtime input in service and provided as expression in pipeline
    // or reference in expression in service
    // we can't filter primary artifact source in this case, hence removing the sources block
    if (EngineExpressionEvaluator.hasExpressions(primaryArtifactRefValue)) {
      primaryArtifactObjectNode.remove(YamlTypes.ARTIFACT_SOURCES);
      return;
    }

    ObjectMapper objectMapper = new ObjectMapper();
    ArrayNode filteredArtifactSourcesNode = objectMapper.createArrayNode();
    List<YamlNode> artifactSources = artifactSourcesField.getNode().asArray();
    for (YamlNode artifactSource : artifactSources) {
      String sourceIdentifier = artifactSource.getIdentifier();
      if (primaryArtifactRefValue.equals(sourceIdentifier)) {
        filteredArtifactSourcesNode.add(artifactSource.getCurrJsonNode());
        break;
      }
    }

    if (EmptyPredicate.isEmpty(filteredArtifactSourcesNode)) {
      if (artifactSources.size() == 1) {
        filteredArtifactSourcesNode.add(artifactSources.get(0).getCurrJsonNode());
      } else {
        throw new InvalidRequestException(
            String.format("Primary artifact ref value %s provided does not exist in sources in service %s",
                primaryArtifactRefValue, serviceIdentifier));
      }
    }
    primaryArtifactObjectNode.set(YamlTypes.ARTIFACT_SOURCES, filteredArtifactSourcesNode);
  }

  public String resolveArtifactSourceTemplateRefs(String accountId, String orgId, String projectId, String yaml) {
    if (TemplateRefHelper.hasTemplateRef(yaml)) {
      String TEMPLATE_RESOLVE_EXCEPTION_MSG = "Exception in resolving template refs in given service yaml.";
      long start = System.currentTimeMillis();
      try {
        TemplateMergeResponseDTO templateMergeResponseDTO =
            NGRestUtils.getResponse(templateResourceClient.applyTemplatesOnGivenYamlV2(accountId, orgId, projectId,
                null, null, null, null, null, null, null, null, null,
                TemplateApplyRequestDTO.builder()
                    .originalEntityYaml(yaml)
                    .checkForAccess(true)
                    .getMergedYamlWithTemplateField(false)
                    .build(),
                false));
        return templateMergeResponseDTO.getMergedPipelineYaml();
      } catch (Exception ex) {
        throw new InvalidRequestException(TEMPLATE_RESOLVE_EXCEPTION_MSG, ex);
      } finally {
        log.info("[NG_MANAGER] template resolution for service took {}ms for projectId {}, orgId {}, accountId {}",
            System.currentTimeMillis() - start, projectId, orgId, accountId);
      }
    }
    return yaml;
  }

  private boolean forceDeleteInternal(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    List<String> services = getServiceIdentifiers(accountId, orgIdentifier, projectIdentifier);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = serviceRepository.deleteMany(criteria);
      if (deleteResult.wasAcknowledged()) {
        if (isEmpty(services)) {
          return true;
        }
        for (String serviceId : services) {
          entitySetupUsageHelper.deleteSetupUsagesWithOnlyIdentifierInfo(
              serviceId, accountId, orgIdentifier, projectIdentifier);
        }

      } else {
        throw new InvalidRequestException(String.format(
            "Services under Project[%s], Organization [%s] couldn't be deleted.", projectIdentifier, orgIdentifier));
      }
      return true;
    }));
  }

  @Override
  public boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");

    return forceDeleteInternal(accountId, orgIdentifier, projectIdentifier);
  }

  @Override
  public boolean forceDeleteAllInOrg(String accountId, String orgIdentifier) {
    checkArgument(isNotEmpty(accountId), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");

    return forceDeleteInternal(accountId, orgIdentifier, null);
  }

  @Override
  public YamlNode getYamlNodeForFqn(String accountId, String orgIdentifier, String projectIdentifier,
      @NotEmpty String serviceIdentifier, String fqn) {
    Optional<ServiceEntity> entity = get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
    if (entity.isEmpty()) {
      throw new InvalidRequestException(format("Service: %s does not exist", serviceIdentifier));
    }

    int index = 0;
    String[] split = fqn.split("\\.");
    final String serviceDefinitionLimiter = "serviceDefinition";
    for (int i = 0; i < split.length; i++) {
      if (serviceDefinitionLimiter.equals(split[i])) {
        index = i;
        break;
      }
    }

    if (index == 0) {
      throw new InvalidRequestException(format("FQN must contain %s", serviceDefinitionLimiter));
    }

    List<String> splitPaths = List.of(split).subList(index, split.length);
    String fqnWithinServiceEntityYaml = String.join(".", splitPaths);

    YamlNode service;
    try {
      service = YamlNode.fromYamlPath(entity.get().fetchNonEmptyYaml(), "service");
    } catch (IOException e) {
      throw new InvalidRequestException("Service entity yaml must be rooted at \"service\"");
    }

    if (service == null) {
      throw new InvalidRequestException("Service entity yaml must be rooted at \"service\"");
    }

    YamlNode leafNode = YamlNodeUtils.goToPathUsingFqn(service, fqnWithinServiceEntityYaml);
    if (leafNode == null) {
      throw new InvalidRequestException(
          format("Unable to locate path %s within service yaml", fqnWithinServiceEntityYaml));
    }
    return leafNode;
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

  @VisibleForTesting
  String getDuplicateServiceExistsErrorMessage(String accountId, String exceptionString) {
    String errorMessageToBeReturned;
    try {
      JSONObject jsonObjectOfDuplicateKey = DuplicateKeyExceptionParser.getDuplicateKey(exceptionString);
      if (jsonObjectOfDuplicateKey != null) {
        String orgIdentifier = jsonObjectOfDuplicateKey.getString("orgIdentifier");
        String projectIdentifier = jsonObjectOfDuplicateKey.getString("projectIdentifier");
        String identifier = jsonObjectOfDuplicateKey.getString("identifier");
        errorMessageToBeReturned =
            getDuplicateServiceExistsErrorMessage(accountId, orgIdentifier, projectIdentifier, identifier);
      } else {
        errorMessageToBeReturned = "A Duplicate Service already exists";
      }
    } catch (Exception ex) {
      errorMessageToBeReturned = "A Duplicate Service already exists";
    }
    return errorMessageToBeReturned;
  }

  @VisibleForTesting
  List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Integer pageSize, boolean includeDeletedServices, List<String> sort) {
    List<ServiceEntity> serviceEntityList = new ArrayList<>();

    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId)
                            .is(accountIdentifier)
                            .and(ServiceEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ServiceEntityKeys.projectIdentifier)
                            .is(projectIdentifier);

    if (!includeDeletedServices) {
      criteria.and(ServiceEntityKeys.deleted).is(false);
    }

    int pageNum = 0;
    // Query in batches of 10k
    while (true) {
      Pageable pageRequest;
      if (isEmpty(sort)) {
        pageRequest = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
      } else {
        pageRequest = PageUtils.getPageRequest(pageNum, pageSize, sort);
      }
      Page<ServiceEntity> pageResponse = serviceRepository.findAll(criteria, pageRequest);
      if (pageResponse.isEmpty()) {
        break;
      }
      serviceEntityList.addAll(pageResponse.getContent());
      pageNum += 1;
    }

    return serviceEntityList;
  }
  @Override
  public List<String> getServiceIdentifiers(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return serviceRepository.getServiceIdentifiers(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  private void validateTheServicesList(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(
        serviceEntity -> validatePresenceOfRequiredFields(serviceEntity.getAccountId(), serviceEntity.getIdentifier()));
  }

  private void populateDefaultNameIfNotPresent(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(this::setNameIfNotPresent);
  }

  private void modifyServiceRequest(ServiceEntity requestService) {
    requestService.setName(requestService.getName().trim());
    // handle empty scope identifiers
    requestService.setOrgIdentifier(
        EmptyPredicate.isEmpty(requestService.getOrgIdentifier()) ? null : requestService.getOrgIdentifier());
    requestService.setProjectIdentifier(
        EmptyPredicate.isEmpty(requestService.getProjectIdentifier()) ? null : requestService.getProjectIdentifier());
  }

  private void modifyServiceRequestBatch(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(this::modifyServiceRequest);
  }

  void processQuietly(BooleanSupplier b) {
    try {
      b.getAsBoolean();
      // supplier processed
    } catch (Exception ex) {
      log.error("failed to process entity deletion", ex);
      // ignore this
    }
  }

  public Optional<ServiceEntity> getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputs(
      String accountId, String orgId, String projectId, String serviceIdentifier, String loadFromCache) {
    checkArgument(isNotEmpty(accountId), ACCOUNT_ID_MUST_BE_PRESENT_ERR_MSG);
    checkArgument(isNotEmpty(serviceIdentifier), "service identifier must be present");

    Optional<ServiceEntity> optionalService = get(accountId, orgId, projectId, serviceIdentifier, false,
        GitXUtils.parseLoadFromCacheHeaderParam(loadFromCache), false);

    if (optionalService.isPresent()) {
      String yaml = optionalService.get().fetchNonEmptyYaml();

      if (TemplateRefHelper.hasTemplateRef(yaml)) {
        return NGRestUtils.getResponse(
            templateResourceClient.validateTemplateInputsForGivenYaml(accountId, orgId, projectId, null, null, null,
                null, null, null, null, null, loadFromCache, RefreshRequestDTO.builder().yaml(yaml).build()));
      }
    }

    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
  }

  @Override
  public ManifestsResponseDTO getManifestIdentifiers(String yaml, String serviceIdentifier) {
    try {
      YamlField serviceYamlField = YamlUtils.readTree(yaml).getNode().getField(YamlTypes.SERVICE_ENTITY);
      if (serviceYamlField == null) {
        throw new YamlException(
            String.format("Yaml provided for service %s does not have service root field.", serviceIdentifier));
      }

      YamlField manifestsField =
          ManifestFilterHelper.getManifestsNodeFromServiceYaml(serviceYamlField, serviceIdentifier);
      if (manifestsField == null) {
        return new ManifestsResponseDTO();
      }

      return new ManifestsResponseDTO().identifiers(
          ManifestFilterHelper.getManifestIdentifiersFilteredOnManifestType(manifestsField));
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Error occurred while fetching list of manifests for service %s", serviceIdentifier), e);
    }
  }

  @Override
  public RepoListResponseDTO getListOfRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      boolean includeAllServicesAccessibleAtScope) {
    Criteria criteria = ServiceFilterHelper.createCriteriaForGetList(
        accountIdentifier, orgIdentifier, projectIdentifier, null, false, includeAllServicesAccessibleAtScope, null);

    List<String> uniqueRepos = serviceRepository.getListOfDistinctRepos(criteria);
    CollectionUtils.filter(uniqueRepos, PredicateUtils.notNullPredicate());

    return RepoListResponseDTO.builder().repositories(uniqueRepos).build();
  }
}