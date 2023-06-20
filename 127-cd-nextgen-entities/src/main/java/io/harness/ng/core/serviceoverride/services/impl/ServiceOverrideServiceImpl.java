/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverrideEventDTOMapper;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceOverrideServiceImpl implements ServiceOverrideService {
  private final ServiceOverrideRepository serviceOverrideRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private final Producer eventProducer;
  private final OutboxService outboxService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;

  @Inject
  public ServiceOverrideServiceImpl(ServiceOverrideRepository serviceOverrideRepository,
      EntitySetupUsageService entitySetupUsageService, @Named(ENTITY_CRUD) Producer eventProducer,
      OutboxService outboxService, TransactionTemplate transactionTemplate) {
    this.serviceOverrideRepository = serviceOverrideRepository;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Optional<NGServiceOverridesEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");

    return getByEnvironmentRef(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
  }

  // Todo: This code handles all four cases where request and entity in DB can contain any of environment Ref or
  // identifier. Clean up in future after successful migration
  private Optional<NGServiceOverridesEntity> getByEnvironmentRef(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    String[] environmentRefSplit = StringUtils.split(environmentRef, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    final String environmentIdentifier;
    final String qualifiedEnvironmentRef;

    if (environmentRefSplit == null || environmentRefSplit.length == 1) {
      environmentIdentifier = environmentRef;
      qualifiedEnvironmentRef =
          IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgIdentifier, projectIdentifier, environmentRef);
      Optional<NGServiceOverridesEntity> serviceOverrideUsingQualifiedRef =
          serviceOverrideRepository
              .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvironmentRefAndServiceRefAndTypeAndYamlExistsAndYamlNotNull(
                  accountId, orgIdentifier, projectIdentifier, qualifiedEnvironmentRef, serviceRef,
                  ServiceOverridesType.ENV_SERVICE_OVERRIDE);

      return serviceOverrideUsingQualifiedRef.isPresent()
          ? serviceOverrideUsingQualifiedRef
          : serviceOverrideRepository
                .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvironmentRefAndServiceRefAndTypeAndYamlExistsAndYamlNotNull(
                    accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceRef,
                    ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    } else {
      IdentifierRef envIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(environmentRef, accountId, orgIdentifier, projectIdentifier);
      environmentIdentifier = envIdentifierRef.getIdentifier();
      qualifiedEnvironmentRef = environmentRef;
      Optional<NGServiceOverridesEntity> serviceOverrideUsingQualifiedRef =
          serviceOverrideRepository
              .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvironmentRefAndServiceRefAndTypeAndYamlExistsAndYamlNotNull(
                  envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
                  envIdentifierRef.getProjectIdentifier(), qualifiedEnvironmentRef, serviceRef,
                  ServiceOverridesType.ENV_SERVICE_OVERRIDE);

      return serviceOverrideUsingQualifiedRef.isPresent()
          ? serviceOverrideUsingQualifiedRef
          : serviceOverrideRepository
                .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvironmentRefAndServiceRefAndTypeAndYamlExistsAndYamlNotNull(
                    envIdentifierRef.getAccountIdentifier(), envIdentifierRef.getOrgIdentifier(),
                    envIdentifierRef.getProjectIdentifier(), environmentIdentifier, serviceRef,
                    ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    }
  }

  @Override
  public NGServiceOverridesEntity upsert(NGServiceOverridesEntity requestServiceOverride) {
    // todo: FF based check
    validatePresenceOfRequiredFields(requestServiceOverride.getAccountId(), requestServiceOverride.getEnvironmentRef(),
        requestServiceOverride.getServiceRef());
    validateOverrideValues(requestServiceOverride);
    Criteria criteria = getServiceOverrideEqualityCriteria(requestServiceOverride);

    Optional<NGServiceOverridesEntity> serviceOverrideOptional = get(requestServiceOverride.getAccountId(),
        requestServiceOverride.getOrgIdentifier(), requestServiceOverride.getProjectIdentifier(),
        requestServiceOverride.getEnvironmentRef(), requestServiceOverride.getServiceRef());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      NGServiceOverridesEntity tempResult = serviceOverrideRepository.upsert(criteria, requestServiceOverride);
      if (tempResult == null) {
        throw new InvalidRequestException(String.format(
            "NGServiceOverridesEntity under Project[%s], Organization [%s], Environment [%s] and Service [%s] couldn't be upserted or doesn't exist.",
            requestServiceOverride.getProjectIdentifier(), requestServiceOverride.getOrgIdentifier(),
            requestServiceOverride.getEnvironmentRef(), requestServiceOverride.getServiceRef()));
      }
      if (serviceOverrideOptional.isPresent()) {
        try {
          outboxService.save(EnvironmentUpdatedEvent.builder()
                                 .accountIdentifier(requestServiceOverride.getAccountId())
                                 .orgIdentifier(requestServiceOverride.getOrgIdentifier())
                                 .status(EnvironmentUpdatedEvent.Status.UPDATED)
                                 .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                                 .projectIdentifier(requestServiceOverride.getProjectIdentifier())
                                 .newOverrideAuditEventDTO(
                                     ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(requestServiceOverride))
                                 .oldOverrideAuditEventDTO(ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(
                                     serviceOverrideOptional.get()))
                                 .overrideAuditV2(true)
                                 .build());
        } catch (IOException e) {
          throw new InvalidRequestException("Failed to save event for override", e);
        }
      } else {
        try {
          outboxService.save(EnvironmentUpdatedEvent.builder()
                                 .accountIdentifier(requestServiceOverride.getAccountId())
                                 .orgIdentifier(requestServiceOverride.getOrgIdentifier())
                                 .status(EnvironmentUpdatedEvent.Status.CREATED)
                                 .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                                 .projectIdentifier(requestServiceOverride.getProjectIdentifier())
                                 .newOverrideAuditEventDTO(
                                     ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(requestServiceOverride))
                                 .overrideAuditV2(true)
                                 .build());
        } catch (IOException e) {
          throw new InvalidRequestException("Failed to save event for override", e);
        }
      }

      return tempResult;
    }));
  }

  void validateOverrideValues(NGServiceOverridesEntity requestServiceOverride) {
    List<NGVariable> variableOverrides = null;
    if (EmptyPredicate.isNotEmpty(requestServiceOverride.getYaml())) {
      try {
        final NGServiceOverrideConfig config =
            YamlPipelineUtils.read(requestServiceOverride.getYaml(), NGServiceOverrideConfig.class);
        variableOverrides = config.getServiceOverrideInfoConfig().getVariables();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create Service Overrides config due to " + e.getMessage());
      }
    }
    if (variableOverrides != null) {
      variableOverrides.removeIf(Objects::isNull);
      Set<String> variableKeys = new HashSet<>();
      Set<String> duplicates = new HashSet<>();
      int emptyOverrideNames = 0;
      int nullOverrideValues = 0;
      String nullOverrideValuesList = "";
      for (NGVariable variableOverride : variableOverrides) {
        if (StringUtils.isBlank(variableOverride.getName())) {
          emptyOverrideNames++;
        } else if (!variableKeys.add(variableOverride.getName())) {
          duplicates.add(variableOverride.getName());
        }

        if (variableOverride.fetchValue().fetchFinalValue() == null) {
          nullOverrideValues++;
          nullOverrideValuesList = nullOverrideValuesList + " " + variableOverride.getName();
        }
      }
      if (emptyOverrideNames != 0) {
        String plural = emptyOverrideNames == 1 ? "" : "s";
        throw new InvalidRequestException(
            String.format("Empty variable name%s for %s variable override%s in service ref: [%s]", plural,
                emptyOverrideNames, plural, requestServiceOverride.getServiceRef()));
      }
      if (nullOverrideValues != 0) {
        String plural = nullOverrideValues == 1 ? "" : "s";
        throw new InvalidRequestException(
            String.format("value%s not provided for %s variable override%s%s in service ref: [%s]", plural,
                nullOverrideValues, plural, nullOverrideValuesList, requestServiceOverride.getServiceRef()));
      }
      if (!duplicates.isEmpty()) {
        throw new InvalidRequestException(String.format("Duplicate Service overrides provided: [%s] for service: [%s]",
            Joiner.on(",").skipNulls().join(duplicates), requestServiceOverride.getServiceRef()));
      }
    }
  }

  @Override
  public Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageable) {
    return serviceOverrideRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(accountId)
                                                          .orgIdentifier(orgIdentifier)
                                                          .projectIdentifier(projectIdentifier)
                                                          .environmentRef(environmentRef)
                                                          .serviceRef(serviceRef)
                                                          .build();

    // todo: check for override usage in pipelines
    Criteria criteria = getServiceOverrideEqualityCriteria(serviceOverridesEntity);

    Optional<NGServiceOverridesEntity> entityOptional =
        get(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
    if (entityOptional.isPresent()) {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        DeleteResult deleteResult = serviceOverrideRepository.delete(criteria);
        if (!deleteResult.wasAcknowledged() || deleteResult.getDeletedCount() != 1) {
          throw new InvalidRequestException(String.format(
              "Service Override for Service [%s], Environment [%s], Project[%s], Organization [%s] couldn't be deleted.",
              serviceRef, environmentRef, projectIdentifier, orgIdentifier));
        }
        try {
          outboxService.save(
              EnvironmentUpdatedEvent.builder()
                  .accountIdentifier(accountId)
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .status(EnvironmentUpdatedEvent.Status.DELETED)
                  .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                  .oldOverrideAuditEventDTO(ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(entityOptional.get()))
                  .overrideAuditV2(true)
                  .build());
        } catch (IOException e) {
          throw new InvalidRequestException("Failed to save event for override", e);
        }
        return true;
      }));
    } else {
      throw new InvalidRequestException(String.format(
          "Service Override for Service [%s], Environment [%s], Project[%s], Organization [%s] doesn't exist.",
          serviceRef, environmentRef, projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public boolean deleteAllInEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(environmentRef), "environment ref must be present");

    Criteria criteria =
        getServiceOverrideEqualityCriteriaForEnv(accountId, orgIdentifier, projectIdentifier, environmentRef);
    DeleteResult delete = serviceOverrideRepository.delete(criteria);
    return delete.wasAcknowledged();
  }

  @Override
  public boolean deleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgId must be present");
    checkArgument(isNotEmpty(projectIdentifier), "projectId must be present");

    return deleteAllInternal(accountId, orgIdentifier, projectIdentifier);
  }

  private boolean deleteAllInternal(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = getServiceOverrideEqualityCriteria(accountId, orgIdentifier, projectIdentifier);
    DeleteResult delete = serviceOverrideRepository.delete(criteria);
    return delete.wasAcknowledged();
  }

  @Override
  public boolean deleteAllInOrg(String accountId, String orgIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgId must be present");

    return deleteAllInternal(accountId, orgIdentifier, null);
  }

  @Override
  public boolean deleteAllInProjectForAService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(serviceIdentifier), "service identifier must be present");

    // build service ref from service identifier
    Criteria criteria;
    String[] serviceRefSplit = serviceIdentifier.split("\\.", 2);
    if (serviceRefSplit.length == 1) {
      // identifier
      IdentifierRef serviceIdentifierRef =
          IdentifierRefHelper.getIdentifierRefWithScope(accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
      String scopedServiceRef = serviceIdentifierRef.buildScopedIdentifier();
      // delete all service overrides with matching serviceRef irrespective of its scope
      criteria = getServiceOverrideEqualityCriteriaForServiceRef(
          accountId, orgIdentifier, projectIdentifier, scopedServiceRef);
    } else {
      // ref
      String scopedServiceRef = serviceIdentifier.trim();
      IdentifierRef serviceIdentifierRef =
          IdentifierRefHelper.getIdentifierRef(serviceIdentifier, accountId, orgIdentifier, projectIdentifier);

      // delete all service overrides with matching serviceRef irrespective of its scope
      criteria = getServiceOverrideEqualityCriteriaForServiceRef(serviceIdentifierRef.getAccountIdentifier(),
          serviceIdentifierRef.getOrgIdentifier(), serviceIdentifierRef.getProjectIdentifier(), scopedServiceRef);
    }
    DeleteResult delete = serviceOverrideRepository.delete(criteria);
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  @Override
  public String createServiceOverrideInputsYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    Map<String, Object> yamlInputs = createServiceOverrideInputsYamlInternal(
        accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlPipelineUtils.writeYamlString(yamlInputs);
  }

  public Map<String, Object> createServiceOverrideInputsYamlInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    Map<String, Object> yamlInputs = new HashMap<>();
    Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional =
        get(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
    if (serviceOverridesEntityOptional.isPresent()) {
      String yaml = serviceOverridesEntityOptional.get().getYaml();
      if (isEmpty(yaml)) {
        throw new InvalidRequestException("Service overrides yaml is empty.");
      }
      try {
        String serviceOverrideInputs = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(yaml);
        if (isEmpty(serviceOverrideInputs)) {
          return null;
        }
        YamlField serviceOverridesYamlField =
            YamlUtils.readTree(serviceOverrideInputs).getNode().getField(YamlTypes.SERVICE_OVERRIDE);
        ObjectNode serviceOverridesNode = (ObjectNode) serviceOverridesYamlField.getNode().getCurrJsonNode();

        yamlInputs.put(YamlTypes.SERVICE_OVERRIDE_INPUTS, serviceOverridesNode);
      } catch (IOException e) {
        throw new InvalidRequestException("Error occurred while creating Service Override inputs ", e);
      }
    }
    return yamlInputs;
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  // Todo: This code handles all four cases where request and entity in DB can contain any of environment Ref or
  // identifier. Clean up in future after successful migration
  private Criteria getServiceOverrideEqualityCriteria(NGServiceOverridesEntity requestServiceOverride) {
    String[] environmentRefSplit =
        StringUtils.split(requestServiceOverride.getEnvironmentRef(), ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    Criteria baseCriteria;
    final String environmentIdentifier;
    final String qualifiedEnvironmentRef;

    if (environmentRefSplit == null || environmentRefSplit.length == 1) {
      environmentIdentifier = requestServiceOverride.getEnvironmentRef();
      qualifiedEnvironmentRef = IdentifierRefHelper.getRefFromIdentifierOrRef(requestServiceOverride.getAccountId(),
          requestServiceOverride.getOrgIdentifier(), requestServiceOverride.getProjectIdentifier(),
          requestServiceOverride.getEnvironmentRef());
      baseCriteria = Criteria.where(NGServiceOverridesEntityKeys.accountId)
                         .is(requestServiceOverride.getAccountId())
                         .and(NGServiceOverridesEntityKeys.orgIdentifier)
                         .is(requestServiceOverride.getOrgIdentifier())
                         .and(NGServiceOverridesEntityKeys.projectIdentifier)
                         .is(requestServiceOverride.getProjectIdentifier())
                         .and(NGServiceOverridesEntityKeys.serviceRef)
                         .is(requestServiceOverride.getServiceRef());
    } else {
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(requestServiceOverride.getEnvironmentRef(),
          requestServiceOverride.getAccountId(), requestServiceOverride.getOrgIdentifier(),
          requestServiceOverride.getProjectIdentifier());
      environmentIdentifier = envIdentifierRef.getIdentifier();
      qualifiedEnvironmentRef = requestServiceOverride.getEnvironmentRef();
      baseCriteria = Criteria.where(NGServiceOverridesEntityKeys.accountId)
                         .is(envIdentifierRef.getAccountIdentifier())
                         .and(NGServiceOverridesEntityKeys.orgIdentifier)
                         .is(envIdentifierRef.getOrgIdentifier())
                         .and(NGServiceOverridesEntityKeys.projectIdentifier)
                         .is(envIdentifierRef.getProjectIdentifier())
                         .and(NGServiceOverridesEntityKeys.serviceRef)
                         .is(requestServiceOverride.getServiceRef());
    }
    // to exclude other type of overrides present in V2
    baseCriteria.and(NGServiceOverridesEntityKeys.type).is(ServiceOverridesType.ENV_SERVICE_OVERRIDE);

    return baseCriteria.andOperator(new Criteria().orOperator(
        Criteria.where(NGServiceOverridesEntityKeys.environmentRef).is(qualifiedEnvironmentRef),
        Criteria.where(NGServiceOverridesEntityKeys.environmentRef).is(environmentIdentifier)));
  }

  // Todo: This code handles all four cases where request and entity in DB can contain any of environment Ref or
  // identifier. Clean up in future after successful migration
  private Criteria getServiceOverrideEqualityCriteriaForEnv(
      String accountId, String orgId, String projId, String envId) {
    String[] environmentRefSplit = StringUtils.split(envId, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    Criteria baseCriteria;
    final String environmentIdentifier;
    final String qualifiedEnvironmentRef;

    if (environmentRefSplit == null || environmentRefSplit.length == 1) {
      qualifiedEnvironmentRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projId, envId);
      environmentIdentifier = envId;
      baseCriteria = Criteria.where(NGServiceOverridesEntityKeys.accountId)
                         .is(accountId)
                         .and(NGServiceOverridesEntityKeys.orgIdentifier)
                         .is(orgId)
                         .and(NGServiceOverridesEntityKeys.projectIdentifier)
                         .is(projId);

    } else {
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(envId, accountId, orgId, projId);
      qualifiedEnvironmentRef = envId;
      environmentIdentifier = envIdentifierRef.getIdentifier();
      baseCriteria = Criteria.where(NGServiceOverridesEntityKeys.accountId)
                         .is(envIdentifierRef.getAccountIdentifier())
                         .and(NGServiceOverridesEntityKeys.orgIdentifier)
                         .is(envIdentifierRef.getOrgIdentifier())
                         .and(NGServiceOverridesEntityKeys.projectIdentifier)
                         .is(envIdentifierRef.getProjectIdentifier());
    }

    // to exclude other type of overrides present in V2
    baseCriteria.and(NGServiceOverridesEntityKeys.type).is(ServiceOverridesType.ENV_SERVICE_OVERRIDE);

    return baseCriteria.andOperator(new Criteria().orOperator(
        Criteria.where(NGServiceOverridesEntityKeys.environmentRef).is(qualifiedEnvironmentRef),
        Criteria.where(NGServiceOverridesEntityKeys.environmentRef).is(environmentIdentifier)));
  }

  // Criteria to delete all service overrides with the given serviceRef regardless of the level its overridden at
  private Criteria getServiceOverrideEqualityCriteriaForServiceRef(
      String accountId, String orgId, String projId, String serviceRef) {
    Criteria criteria = new Criteria();
    criteria.and(NGServiceOverridesEntityKeys.accountId).is(accountId);
    if (isNotEmpty(orgId)) {
      criteria.and(NGServiceOverridesEntityKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projId)) {
      criteria.and(NGServiceOverridesEntityKeys.projectIdentifier).is(projId);
    }
    // to exclude other type of overrides present in V2
    criteria.and(NGServiceOverridesEntityKeys.serviceRef).is(serviceRef);
    criteria.and(NGServiceOverridesEntityKeys.type).is(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
    return criteria;
  }

  private Criteria getServiceOverrideEqualityCriteria(String accountId, String orgId, String projId) {
    return Criteria.where(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(orgId)
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(projId)
        .and(NGServiceOverridesEntityKeys.type)
        .is(ServiceOverridesType.ENV_SERVICE_OVERRIDE);
  }
}
