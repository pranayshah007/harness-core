/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.HARD_DELETE_ENTITIES;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.YamlException;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.events.ServiceCreateEvent;
import io.harness.ng.core.events.ServiceDeleteEvent;
import io.harness.ng.core.events.ServiceUpdateEvent;
import io.harness.ng.core.events.ServiceUpsertEvent;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ServiceEntityServiceImpl implements ServiceEntityService {
  private final ServiceRepository serviceRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private static final Integer QUERY_PAGE_SIZE = 10000;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_TRANSACTION_RETRY_POLICY;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Service [%s] under Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Service [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT = "Service [%s] in Account [%s] already exists";

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  @Override
  public ServiceEntity create(@NotNull @Valid ServiceEntity serviceEntity) {
    try {
      validatePresenceOfRequiredFields(serviceEntity.getAccountId(), serviceEntity.getIdentifier());
      setNameIfNotPresent(serviceEntity);
      modifyServiceRequest(serviceEntity);
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        ServiceEntity service = serviceRepository.save(serviceEntity);
        outboxService.save(ServiceCreateEvent.builder()
                               .accountIdentifier(serviceEntity.getAccountId())
                               .orgIdentifier(serviceEntity.getOrgIdentifier())
                               .projectIdentifier(serviceEntity.getProjectIdentifier())
                               .service(serviceEntity)
                               .build());
        return service;
      }));

    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(),
              serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier()),
          USER_SRE, ex);
    }
  }

  @Override
  public Optional<ServiceEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, boolean deleted) {
    return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, !deleted);
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
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        ServiceEntity updatedResult = serviceRepository.update(criteria, requestService);
        if (updatedResult == null) {
          throw new InvalidRequestException(
              String.format("Service [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                  requestService.getIdentifier(), requestService.getProjectIdentifier(),
                  requestService.getOrgIdentifier()));
        }
        outboxService.save(ServiceUpdateEvent.builder()
                               .accountIdentifier(requestService.getAccountId())
                               .orgIdentifier(requestService.getOrgIdentifier())
                               .projectIdentifier(requestService.getProjectIdentifier())
                               .newService(updatedResult)
                               .oldService(oldService)
                               .build());
        return updatedResult;
      }));
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
    Criteria criteria = getServiceEqualityCriteria(requestService, requestService.getDeleted());
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      ServiceEntity result = serviceRepository.upsert(criteria, requestService);
      if (result == null) {
        throw new InvalidRequestException(String.format(
            "Service [%s] under Project[%s], Organization [%s] couldn't be upserted.", requestService.getIdentifier(),
            requestService.getProjectIdentifier(), requestService.getOrgIdentifier()));
      }
      if (upsertOptions.isSendOutboxEvent()) {
        outboxService.save(ServiceUpsertEvent.builder()
                               .accountIdentifier(requestService.getAccountId())
                               .orgIdentifier(requestService.getOrgIdentifier())
                               .projectIdentifier(requestService.getProjectIdentifier())
                               .service(requestService)
                               .build());
      }
      return result;
    }));
  }

  @Override
  public Page<ServiceEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable) {
    return serviceRepository.findAll(criteria, pageable);
  }

  @Override
  public List<ServiceEntity> listRunTimePermission(Criteria criteria) {
    return serviceRepository.findAllRunTimePermission(criteria);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, Long version) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(serviceIdentifier), "serviceIdentifier must be present");

    final boolean shouldHardDelete = ngFeatureFlagHelperService.isEnabled(accountId, HARD_DELETE_ENTITIES);

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(serviceIdentifier)
                                      .version(version)
                                      .build();
    checkThatServiceIsNotReferredByOthers(serviceEntity);
    Criteria criteria = getServiceEqualityCriteria(serviceEntity, false);
    Optional<ServiceEntity> serviceEntityOptional =
        get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);

    if (serviceEntityOptional.isPresent()) {
      return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ServiceEntity serviceEntityRetrieved = serviceEntityOptional.get();
        final boolean deleted =
            shouldHardDelete ? serviceRepository.delete(criteria) : serviceRepository.softDelete(criteria);
        if (!deleted) {
          throw new InvalidRequestException(
              String.format("Service [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                  serviceEntity.getIdentifier(), projectIdentifier, orgIdentifier));
        }

        outboxService.save(new ServiceDeleteEvent(accountId, serviceEntityRetrieved.getOrgIdentifier(),
            serviceEntityRetrieved.getProjectIdentifier(), serviceEntityRetrieved));
        return true;
      }));
    } else {
      throw new InvalidRequestException(
          String.format("Service [%s] under Project[%s], Organization [%s] doesn't exist.", serviceIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  private void checkThatServiceIsNotReferredByOthers(ServiceEntity serviceEntity) {
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

  private Criteria getServiceEqualityCriteria(@Valid ServiceEntity requestService, boolean deleted) {
    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId)
                            .is(requestService.getAccountId())
                            .and(ServiceEntityKeys.orgIdentifier)
                            .is(requestService.getOrgIdentifier())
                            .and(ServiceEntityKeys.projectIdentifier)
                            .is(requestService.getProjectIdentifier())
                            .and(ServiceEntityKeys.identifier)
                            .is(requestService.getIdentifier())
                            .and(ServiceEntityKeys.deleted)
                            .is(deleted);
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
      List<ServiceEntity> outputServiceEntitiesList = (List<ServiceEntity>) serviceRepository.saveAll(serviceEntities);
      return new PageImpl<>(outputServiceEntitiesList);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(accountId, ex.getMessage()), USER_SRE, ex);
    } catch (Exception ex) {
      String serviceNames = serviceEntities.stream().map(ServiceEntity::getName).collect(Collectors.joining(","));
      log.info(
          "Encountered exception while saving the service entity records of [{}], with exception", serviceNames, ex);
      throw new UnexpectedException("Encountered exception while saving the service entity records.");
    }
  }

  @Override
  public List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getAllServices(accountIdentifier, orgIdentifier, projectIdentifier, QUERY_PAGE_SIZE, true);
  }

  @Override
  public List<ServiceEntity> getAllNonDeletedServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getAllServices(accountIdentifier, orgIdentifier, projectIdentifier, QUERY_PAGE_SIZE, false);
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
  public ServiceEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean deleted) {
    return serviceRepository.find(accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifier, deleted);
  }

  @Override
  public String createServiceInputsYaml(String yaml) {
    Map<String, Object> serviceInputs = new HashMap<>();

    try {
      YamlField serviceYamlField = YamlUtils.readTree(yaml).getNode().getField(YamlTypes.SERVICE_ENTITY);
      if (serviceYamlField == null) {
        throw new YamlException("Yaml provided is not a service yaml.");
      }
      ObjectNode serviceNode = (ObjectNode) serviceYamlField.getNode().getCurrJsonNode();
      String serviceDefinition = serviceNode.retain(YamlTypes.SERVICE_DEFINITION).toString();
      if (isEmpty(serviceDefinition)) {
        throw new YamlException("Service yaml provided does not have service definition in it.");
      }
      String serviceDefinitionInputs = RuntimeInputFormHelper.createTemplateFromYaml(serviceDefinition);
      if (isEmpty(serviceDefinitionInputs)) {
        return serviceDefinitionInputs;
      }
      JsonNode serviceDefinitionNode = YamlUtils.readTree(serviceDefinitionInputs).getNode().getCurrJsonNode();
      serviceInputs.put(YamlTypes.SERVICE_INPUTS, serviceDefinitionNode);
      return YamlPipelineUtils.writeYamlString(serviceInputs);
    } catch (IOException e) {
      throw new InvalidRequestException("Error occurred while creating service inputs ", e);
    }
  }

  @Override
  public boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    final boolean shouldHardDelete = ngFeatureFlagHelperService.isEnabled(accountId, HARD_DELETE_ENTITIES);
    if (!shouldHardDelete) {
      return false;
    }
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = serviceRepository.deleteMany(criteria);
      if (!deleteResult.wasAcknowledged()) {
        throw new InvalidRequestException(String.format(
            "Services under Project[%s], Organization [%s] couldn't be deleted.", projectIdentifier, orgIdentifier));
      }
      return true;
    }));
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
      Integer pageSize, boolean includeDeletedServices) {
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
      PageRequest pageRequest =
          PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.ASC, ServiceEntityKeys.createdAt));
      Page<ServiceEntity> pageResponse = serviceRepository.findAll(criteria, pageRequest);
      if (pageResponse.isEmpty()) {
        break;
      }
      serviceEntityList.addAll(pageResponse.getContent());
      pageNum += 1;
    }

    return serviceEntityList;
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
  }

  private void modifyServiceRequestBatch(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(serviceEntity -> modifyServiceRequest(serviceEntity));
  }
}
