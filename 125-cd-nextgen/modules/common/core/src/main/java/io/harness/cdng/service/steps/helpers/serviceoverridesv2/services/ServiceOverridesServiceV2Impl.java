/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.azure.config.yaml.ApplicationSettingsConfiguration;
import io.harness.cdng.azure.config.yaml.ConnectionStringsConfiguration;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.setupusage.ServiceOverridesV2SetupUsageHelper;
import io.harness.cdng.service.steps.helpers.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.encryption.Scope;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InternalServerErrorException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideAuditEventDTO;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.ng.core.serviceoverridev2.mappers.ServiceOverrideEventDTOMapper;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.serviceoverridesv2.custom.ServiceOverrideRepositoryHelper;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.scope.ScopeHelper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.opensaml.xmlsec.signature.P;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceOverridesServiceV2Impl implements ServiceOverridesServiceV2 {
  private final ServiceOverridesRepositoryV2 serviceOverrideRepositoryV2;
  private final OutboxService outboxService;
  private final ServiceOverrideValidatorService overrideValidatorService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  @Inject ServiceOverridesV2SetupUsageHelper serviceOverridesV2SetupUsageHelper;

  private static final ObjectMapper mapper = new ObjectMapper();

  @Inject
  public ServiceOverridesServiceV2Impl(ServiceOverridesRepositoryV2 serviceOverrideRepositoryV2,
      OutboxService outboxService, ServiceOverrideValidatorService overrideValidatorService,
      TransactionTemplate transactionTemplate) {
    this.serviceOverrideRepositoryV2 = serviceOverrideRepositoryV2;
    this.outboxService = outboxService;
    this.overrideValidatorService = overrideValidatorService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Optional<NGServiceOverridesEntity> get(@NonNull String accountId, String orgIdentifier,
      String projectIdentifier, @NonNull String serviceOverridesIdentifier) {
    return serviceOverrideRepositoryV2
        .getNGServiceOverridesEntityByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndSpecExistsAndSpecNotNull(
            accountId, orgIdentifier, projectIdentifier, serviceOverridesIdentifier);
  }

  @Override
  public Optional<NGServiceOverridesEntity> getOverridesContainingYaml(@NonNull String accountId, String orgIdentifier,
      String projectIdentifier, @NonNull String serviceOverridesIdentifier) {
    return serviceOverrideRepositoryV2
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndTypeAndYamlExistsAndYamlNotNull(accountId,
            orgIdentifier, projectIdentifier, serviceOverridesIdentifier, ServiceOverridesType.ENV_SERVICE_OVERRIDE);
  }

  @Override
  public NGServiceOverridesEntity create(@NonNull NGServiceOverridesEntity requestedEntity) {
    validatePresenceOfRequiredFields(
        requestedEntity.getAccountId(), requestedEntity.getEnvironmentRef(), requestedEntity.getType());
    modifyRequestedServiceOverride(requestedEntity);

    Optional<NGServiceOverridesEntity> existingEntity = get(requestedEntity.getAccountId(),
        requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());

    if (existingEntity.isEmpty() && ServiceOverridesType.ENV_SERVICE_OVERRIDE == requestedEntity.getType()) {
      validateV1OverrideNotExistOrThrow(requestedEntity);
    }

    if (existingEntity.isPresent()) {
      throw new InvalidRequestException(
          format("Service Override with identifier [%s] already exists", requestedEntity.getIdentifier()));
    }

    NGServiceOverridesEntity createdEntity =
        Failsafe.with(transactionRetryPolicy)
            .get(() -> transactionTemplate.execute(status -> saveAndSendOutBoxEvent(requestedEntity)));
    Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestedEntity);
    serviceOverridesV2SetupUsageHelper.createSetupUsages(createdEntity, referredEntities);
    return createdEntity;
  }

  private void validateV1OverrideNotExistOrThrow(@NotNull NGServiceOverridesEntity requestedEntity) {
    Optional<NGServiceOverridesEntity> existingEntityContainingYaml =
        getOverridesContainingYaml(requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(),
            requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());
    if (existingEntityContainingYaml.isPresent()) {
      throw new InvalidRequestException(format(
          "Service override of version V1 already exist for environment: %s and service: %s already exist please delete or migrate v1 type of overrides and try again",
          requestedEntity.getEnvironmentRef(), requestedEntity.getServiceRef()));
    }
  }

  @Override
  public NGServiceOverridesEntity update(@NonNull @Valid NGServiceOverridesEntity requestedEntity) throws IOException {
    validatePresenceOfRequiredFields(
        requestedEntity.getAccountId(), requestedEntity.getEnvironmentRef(), requestedEntity.getType());
    modifyRequestedServiceOverride(requestedEntity);
    Criteria equalityCriteria = ServiceOverrideRepositoryHelper.getEqualityCriteriaForServiceOverride(
        requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
        requestedEntity.getIdentifier());
    Optional<NGServiceOverridesEntity> existingEntity = get(requestedEntity.getAccountId(),
        requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());

    if (existingEntity.isEmpty() && ServiceOverridesType.ENV_SERVICE_OVERRIDE == requestedEntity.getType()) {
      validateV1OverrideNotExistOrThrow(requestedEntity);
    }

    if (existingEntity.isPresent()) {
      overrideValidatorService.checkForImmutablePropertiesOrThrow(existingEntity.get(), requestedEntity);
      ServiceOverrideAuditEventDTO oldOverrideAuditEventDTO =
          ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(existingEntity.get());

      NGServiceOverridesEntity updateEntity =
          Failsafe.with(transactionRetryPolicy)
              .get(()
                       -> transactionTemplate.execute(status
                           -> updateAndSendOutboxEvent(requestedEntity, equalityCriteria, oldOverrideAuditEventDTO)));
      Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestedEntity);
      serviceOverridesV2SetupUsageHelper.updateSetupUsages(updateEntity, referredEntities);
      return updateEntity;
    } else {
      throw new InvalidRequestException(format(
          "ServiceOverride [%s] under Project[%s], Organization [%s] doesn't exist.", requestedEntity.getIdentifier(),
          requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier()));
    }
  }

  private Set<EntityDetailProtoDTO> getAndValidateReferredEntities(NGServiceOverridesEntity overridesEntity) {
    try {
      return serviceOverridesV2SetupUsageHelper.getAllReferredEntities(overridesEntity);
    } catch (RuntimeException ex) {
      log.error(
          format("Exception while retrieving referred entities for overrides: [%s]. ", overridesEntity.getIdentifier()),
          ex);
      throw new InternalServerErrorException(
          String.format(
              "Exception while retrieving referred entities for overrides: [%s]. ", overridesEntity.getIdentifier())
          + ex.getMessage());
    }
  }

  @Override
  public boolean delete(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String identifier, NGServiceOverridesEntity existingEntity) {
    if (existingEntity == null) {
      existingEntity = checkIfServiceOverrideExistAndThrow(accountId, orgIdentifier, projectIdentifier, identifier);
    }

    boolean deleted = deleteInternal(accountId, orgIdentifier, projectIdentifier, identifier, existingEntity);
    if (deleted) {
      serviceOverridesV2SetupUsageHelper.deleteSetupUsages(existingEntity);
    }
    return deleted;
  }

  @Override
  public Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageRequest) {
    return serviceOverrideRepositoryV2.findAll(criteria, pageRequest);
  }

  @Override
  public List<NGServiceOverridesEntity> findAll(Criteria criteria) {
    return serviceOverrideRepositoryV2.findAll(criteria);
  }

  @Override
  public Pair<NGServiceOverridesEntity, Boolean> upsert(@NonNull NGServiceOverridesEntity requestedEntity)
      throws IOException {
    validatePresenceOfRequiredFields(
        requestedEntity.getAccountId(), requestedEntity.getEnvironmentRef(), requestedEntity.getType());
    modifyRequestedServiceOverride(requestedEntity);
    Criteria equalityCriteria = ServiceOverrideRepositoryHelper.getEqualityCriteriaForServiceOverride(
        requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
        requestedEntity.getIdentifier());
    Optional<NGServiceOverridesEntity> existingEntityInDb = get(requestedEntity.getAccountId(),
        requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());

    if (existingEntityInDb.isEmpty() && ServiceOverridesType.ENV_SERVICE_OVERRIDE == requestedEntity.getType()) {
      validateV1OverrideNotExistOrThrow(requestedEntity);
    }
    if (existingEntityInDb.isEmpty()) {
      NGServiceOverridesEntity createdOverrideEntity =
          Failsafe.with(transactionRetryPolicy)
              .get(() -> transactionTemplate.execute(status -> saveAndSendOutBoxEvent(requestedEntity)));
      Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestedEntity);
      serviceOverridesV2SetupUsageHelper.createSetupUsages(createdOverrideEntity, referredEntities);
      return new ImmutablePair<>(createdOverrideEntity, true);
    }

    overrideValidatorService.checkForImmutablePropertiesOrThrow(existingEntityInDb.get(), requestedEntity);
    ServiceOverrideAuditEventDTO oldOverrideAuditEventDTO =
        ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(existingEntityInDb.get());
    NGServiceOverridesEntity mergedOverrideEntity =
        mergeRequestedAndExistingOverrides(requestedEntity, existingEntityInDb.get());

    NGServiceOverridesEntity updateOverrideEntity =
        Failsafe.with(transactionRetryPolicy)
            .get(
                ()
                    -> transactionTemplate.execute(status
                        -> updateAndSendOutboxEvent(mergedOverrideEntity, equalityCriteria, oldOverrideAuditEventDTO)));
    Set<EntityDetailProtoDTO> referredEntities = getAndValidateReferredEntities(requestedEntity);
    serviceOverridesV2SetupUsageHelper.updateSetupUsages(updateOverrideEntity, referredEntities);
    return new ImmutablePair<>(updateOverrideEntity, false);
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getEnvOverride(
      @NonNull String accountId, String orgId, String projectId, @NonNull String envRef, NGLogCallback logCallback) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.environmentRef)
                            .is(envRef)
                            .and(NGServiceOverridesEntityKeys.type)
                            .is(ServiceOverridesType.ENV_GLOBAL_OVERRIDE)
                            .and(NGServiceOverridesEntityKeys.spec)
                            .exists(true);

    return getScopedEntities(accountId, orgId, projectId, criteria, logCallback);
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getEnvServiceOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String serviceRef, NGLogCallback logCallback) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.environmentRef)
                            .is(envRef)
                            .and(NGServiceOverridesEntityKeys.serviceRef)
                            .is(serviceRef)
                            .and(NGServiceOverridesEntityKeys.type)
                            .is(ServiceOverridesType.ENV_SERVICE_OVERRIDE)
                            .and(NGServiceOverridesEntityKeys.spec)
                            .exists(true);
    return getScopedEntities(accountId, orgId, projectId, criteria, logCallback);
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getInfraOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String infraId, NGLogCallback logCallback) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.environmentRef)
                            .is(envRef)
                            .and(NGServiceOverridesEntityKeys.infraIdentifier)
                            .is(infraId)
                            .and(NGServiceOverridesEntityKeys.type)
                            .is(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE)
                            .and(NGServiceOverridesEntityKeys.spec)
                            .exists(true);
    return getScopedEntities(accountId, orgId, projectId, criteria, logCallback);
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getInfraServiceOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String serviceRef, @NonNull String infraId,
      NGLogCallback logCallback) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntityKeys.environmentRef)
                            .is(envRef)
                            .and(NGServiceOverridesEntityKeys.serviceRef)
                            .is(serviceRef)
                            .and(NGServiceOverridesEntityKeys.infraIdentifier)
                            .is(infraId)
                            .and(NGServiceOverridesEntityKeys.type)
                            .is(ServiceOverridesType.INFRA_SERVICE_OVERRIDE)
                            .and(NGServiceOverridesEntityKeys.spec)
                            .exists(true);
    return getScopedEntities(accountId, orgId, projectId, criteria, logCallback);
  }

  @Override
  public Optional<NGServiceOverrideConfigV2> mergeOverridesGroupedByType(
      @NonNull List<NGServiceOverridesEntity> overridesEntities) {
    if (overridesEntities.size() > 3) {
      throw new InvalidRequestException(
          "Found more than 3 overrides, at one scope (project, org, account) only one override is supported");
    }

    if (isEmpty(overridesEntities)) {
      return Optional.empty();
    }

    if (overridesEntities.size() == 1) {
      NGServiceOverridesEntity overridesEntity = overridesEntities.get(0);
      return Optional.of(NGServiceOverrideConfigV2.builder()
                             .identifier(overridesEntity.getIdentifier())
                             .serviceRef(overridesEntity.getServiceRef())
                             .environmentRef(overridesEntity.getEnvironmentRef())
                             .infraId(overridesEntity.getInfraIdentifier())
                             .spec(overridesEntity.getSpec())
                             .type(overridesEntity.getType())
                             .build());
    }

    ServiceOverridesSpec finalSpec = getFinalMergedSpecFromOverridesGroupedByType(overridesEntities);

    return Optional.of(NGServiceOverrideConfigV2.builder()
                           .identifier(overridesEntities.get(0).getIdentifier())
                           .serviceRef(overridesEntities.get(0).getServiceRef())
                           .environmentRef(overridesEntities.get(0).getEnvironmentRef())
                           .infraId(overridesEntities.get(0).getInfraIdentifier())
                           .type(overridesEntities.get(0).getType())
                           .spec(finalSpec)
                           .build());
  }

  @Override
  public String createServiceOverrideInputsYaml(@NonNull String accountId, String orgIdentifier,
      String projectIdentifier, @NonNull String environmentRef, @NonNull String serviceRef) {
    Map<String, Object> yamlInputs = createServiceOverrideInputsYamlInternal(
        accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlUtils.writeYamlString(yamlInputs);
  }

  @Override
  public String createEnvOverrideInputsYaml(
      @NonNull String accountId, String orgIdentifier, String projectIdentifier, @NonNull String environmentRef) {
    Map<String, Object> yamlInputs =
        createEnvOverrideInputsYamlInternal(accountId, orgIdentifier, projectIdentifier, environmentRef);
    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlUtils.writeYamlString(yamlInputs);
  }

  private Map<String, Object> createEnvOverrideInputsYamlInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef) {
    Map<String, Object> yamlInputs = new HashMap<>();

    Map<Scope, NGServiceOverridesEntity> envOverrideAtAllScopes =
        getEnvOverride(accountId, orgIdentifier, projectIdentifier, environmentRef, null);
    Optional<NGServiceOverrideConfigV2> ngServiceOverrideConfigV2 = Optional.empty();
    if (isNotEmpty(envOverrideAtAllScopes)) {
      ngServiceOverrideConfigV2 = mergeOverridesGroupedByType(new ArrayList<>(envOverrideAtAllScopes.values()));
    }

    if (ngServiceOverrideConfigV2.isPresent()) {
      try {
        String yaml = getSpecYamlForMerging(ngServiceOverrideConfigV2.get().getSpec());
        String serviceOverrideInputs = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(yaml);
        if (isEmpty(serviceOverrideInputs)) {
          return null;
        }

        YamlField serviceOverridesYamlField =
            YamlUtils.readTree(serviceOverrideInputs).getNode().getField(YamlTypes.SERVICE_OVERRIDE);

        JsonNode variableJsonNode = serviceOverridesYamlField.getNode().getCurrJsonNode().get(YamlTypes.VARIABLES);
        if (variableJsonNode != null) {
          ((ObjectNode) serviceOverridesYamlField.getNode().getCurrJsonNode()).remove(YamlTypes.VARIABLES);
        }

        ObjectNode overridesNode = mapper.createObjectNode();
        if (!serviceOverridesYamlField.getNode().getCurrJsonNode().isEmpty()) {
          overridesNode.set(YamlTypes.OVERRIDE, serviceOverridesYamlField.getNode().getCurrJsonNode());
        }

        ObjectNode dummyNode = mapper.createObjectNode();
        dummyNode.set("Dummy", overridesNode);

        if (variableJsonNode != null) {
          ((ObjectNode) dummyNode.get("Dummy")).set(YamlTypes.VARIABLES, variableJsonNode);
        }

        yamlInputs.put(YamlTypes.ENVIRONMENT_INPUTS, dummyNode.get("Dummy"));
      } catch (IOException e) {
        throw new InvalidRequestException("Error occurred while creating Service Override inputs ", e);
      }
    }

    return yamlInputs;
  }

  private Map<String, Object> createServiceOverrideInputsYamlInternal(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    Map<String, Object> yamlInputs = new HashMap<>();

    Map<Scope, NGServiceOverridesEntity> envServiceOverrideAtAllScopes =
        getEnvServiceOverride(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef, null);
    Optional<NGServiceOverrideConfigV2> ngServiceOverrideConfigV2 = Optional.empty();
    if (isNotEmpty(envServiceOverrideAtAllScopes)) {
      ngServiceOverrideConfigV2 = mergeOverridesGroupedByType(new ArrayList<>(envServiceOverrideAtAllScopes.values()));
    }

    if (ngServiceOverrideConfigV2.isPresent()) {
      try {
        String yaml = getSpecYamlForMerging(ngServiceOverrideConfigV2.get().getSpec());
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

  private String getSpecYamlForMerging(ServiceOverridesSpec spec) throws IOException {
    String specYaml = YamlUtils.writeYamlString(spec);
    YamlField yamlField = YamlUtils.readTree(specYaml);
    JsonNode currJsonNode = yamlField.getNode().getCurrJsonNode();
    ObjectNode dummyObjectNode = mapper.createObjectNode();
    dummyObjectNode.set(YamlTypes.SERVICE_OVERRIDE, currJsonNode);
    YamlConfig yamlConfig = new YamlConfig(dummyObjectNode);
    return yamlConfig.getYaml();
  }

  private NGServiceOverridesEntity saveAndSendOutBoxEvent(@NonNull NGServiceOverridesEntity requestedEntity) {
    NGServiceOverridesEntity tempCreateResult = serviceOverrideRepositoryV2.save(requestedEntity);
    if (tempCreateResult == null) {
      throw new InvalidRequestException(format(
          "NGServiceOverridesEntity under Project[%s], Organization [%s], Environment [%s] and Service [%s] couldn't be created.",
          requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier(),
          requestedEntity.getEnvironmentRef(), requestedEntity.getServiceRef()));
    }

    try {
      outboxService.save(
          EnvironmentUpdatedEvent.builder()
              .accountIdentifier(tempCreateResult.getAccountId())
              .orgIdentifier(tempCreateResult.getOrgIdentifier())
              .status(EnvironmentUpdatedEvent.Status.CREATED)
              .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
              .projectIdentifier(tempCreateResult.getProjectIdentifier())
              .newOverrideAuditEventDTO(ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(tempCreateResult))
              .overrideAuditV2(true)
              .build());
    } catch (IOException e) {
      log.error("Failed to save event for override with identifier: " + requestedEntity.getIdentifier(), e);
    }

    return tempCreateResult;
  }

  private NGServiceOverridesEntity updateAndSendOutboxEvent(@NonNull NGServiceOverridesEntity requestedEntity,
      Criteria equalityCriteria, ServiceOverrideAuditEventDTO oldOverrideAuditEventDTO) {
    NGServiceOverridesEntity updatedServiceOverride =
        serviceOverrideRepositoryV2.update(equalityCriteria, requestedEntity);
    if (updatedServiceOverride == null) {
      throw new InvalidRequestException(format(
          "ServiceOverride [%s] under Project [%s], Organization [%s] couldn't be updated or doesn't exist.",
          requestedEntity.getIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier()));
    }
    try {
      outboxService.save(
          EnvironmentUpdatedEvent.builder()
              .accountIdentifier(updatedServiceOverride.getAccountId())
              .orgIdentifier(updatedServiceOverride.getOrgIdentifier())
              .projectIdentifier(updatedServiceOverride.getProjectIdentifier())
              .newOverrideAuditEventDTO(ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(updatedServiceOverride))
              .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
              .status(EnvironmentUpdatedEvent.Status.UPDATED)
              .oldOverrideAuditEventDTO(oldOverrideAuditEventDTO)
              .overrideAuditV2(true)
              .build());
    } catch (IOException e) {
      log.error("Failed to save event for override with identifier: " + requestedEntity.getIdentifier(), e);
    }
    return updatedServiceOverride;
  }

  private NGServiceOverridesEntity mergeRequestedAndExistingOverrides(
      NGServiceOverridesEntity requestedEntity, NGServiceOverridesEntity existingEntity) {
    if (requestedEntity != null && existingEntity != null) {
      ServiceOverridesSpec requestedSpec = requestedEntity.getSpec();
      ServiceOverridesSpec existingSpec = existingEntity.getSpec();

      existingEntity.setSpec(mergeSpec(requestedSpec, existingSpec));
      return existingEntity;
    }
    return requestedEntity == null ? existingEntity : requestedEntity;
  }

  private ServiceOverridesSpec mergeSpec(ServiceOverridesSpec requestedSpec, ServiceOverridesSpec existingSpec) {
    return ServiceOverridesSpec.builder()
        .variables(mergeVariables(requestedSpec.getVariables(), existingSpec.getVariables()))
        .manifests(mergeManifest(requestedSpec.getManifests(), existingSpec.getManifests()))
        .configFiles(mergeConfigFiles(requestedSpec.getConfigFiles(), existingSpec.getConfigFiles()))
        .applicationSettings(requestedSpec.getApplicationSettings() == null ? existingSpec.getApplicationSettings()
                                                                            : requestedSpec.getApplicationSettings())
        .connectionStrings(requestedSpec.getConnectionStrings() == null ? existingSpec.getConnectionStrings()
                                                                        : requestedSpec.getConnectionStrings())
        .build();
  }

  private List<ConfigFileWrapper> mergeConfigFiles(
      List<ConfigFileWrapper> requestedConfigFiles, List<ConfigFileWrapper> existingConfigFiles) {
    List<ConfigFileWrapper> mergedConfigFiles = new ArrayList<>();
    if (isEmpty(requestedConfigFiles)) {
      mergedConfigFiles = existingConfigFiles;
    } else if (isEmpty(existingConfigFiles)) {
      mergedConfigFiles = requestedConfigFiles;
    } else {
      List<String> requestedConfigFilesIdentifier = requestedConfigFiles.stream()
                                                        .map(ConfigFileWrapper::getConfigFile)
                                                        .map(ConfigFile::getIdentifier)
                                                        .collect(Collectors.toList());
      existingConfigFiles.removeIf(
          configFile -> requestedConfigFilesIdentifier.contains(configFile.getConfigFile().getIdentifier()));
      existingConfigFiles.addAll(requestedConfigFiles);
      mergedConfigFiles = existingConfigFiles;
    }
    return mergedConfigFiles;
  }

  private List<ManifestConfigWrapper> mergeManifest(
      List<ManifestConfigWrapper> requestedManifests, List<ManifestConfigWrapper> existingManifests) {
    List<ManifestConfigWrapper> mergedManifests = new ArrayList<>();
    if (isEmpty(requestedManifests)) {
      mergedManifests = existingManifests;
    } else if (isEmpty(existingManifests)) {
      mergedManifests = requestedManifests;
    } else {
      List<String> requestedManifestsIdentifier = requestedManifests.stream()
                                                      .map(ManifestConfigWrapper::getManifest)
                                                      .map(ManifestConfig::getIdentifier)
                                                      .collect(Collectors.toList());
      existingManifests.removeIf(
          manifest -> requestedManifestsIdentifier.contains(manifest.getManifest().getIdentifier()));
      existingManifests.addAll(requestedManifests);
      mergedManifests = existingManifests;
    }
    return mergedManifests;
  }

  private static List<NGVariable> mergeVariables(List<NGVariable> requestedVars, List<NGVariable> existingVars) {
    List<NGVariable> variables = new ArrayList<>();
    if (isEmpty(requestedVars)) {
      variables = existingVars;
    } else if (isEmpty(existingVars)) {
      variables = requestedVars;
    } else {
      Map<String, NGVariable> existingVarMap =
          existingVars.stream().collect(Collectors.toMap(NGVariable::getName, Function.identity()));
      requestedVars.forEach(ngVar -> existingVarMap.put(ngVar.getName(), ngVar));
      variables = new ArrayList<>(existingVarMap.values());
    }
    return variables;
  }

  private NGServiceOverridesEntity checkIfServiceOverrideExistAndThrow(
      @NonNull String accountId, String orgIdentifier, String projectIdentifier, @NonNull String identifier) {
    Optional<NGServiceOverridesEntity> existingOverrideInDb =
        serviceOverrideRepositoryV2
            .getNGServiceOverridesEntityByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndSpecExistsAndSpecNotNull(
                accountId, orgIdentifier, projectIdentifier, identifier);
    if (existingOverrideInDb.isEmpty()) {
      throw new InvalidRequestException(
          format("Service Override with identifier: [%s], projectId: [%s], orgId: [%s] does not exist", identifier,
              projectIdentifier, orgIdentifier));
    }
    return existingOverrideInDb.get();
  }

  private boolean deleteInternal(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String identifier, @NonNull NGServiceOverridesEntity existingEntity) {
    Criteria equalityCriteria = ServiceOverrideRepositoryHelper.getEqualityCriteriaForServiceOverride(
        accountId, orgIdentifier, projectIdentifier, identifier);

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = serviceOverrideRepositoryV2.delete(equalityCriteria);
      if (!deleteResult.wasAcknowledged() || deleteResult.getDeletedCount() != 1) {
        throw new InvalidRequestException(
            format("Service Override [%s], Project[%s], Organization [%s] couldn't be deleted.", identifier,
                projectIdentifier, orgIdentifier));
      }
      try {
        outboxService.save(
            EnvironmentUpdatedEvent.builder()
                .accountIdentifier(accountId)
                .orgIdentifier(orgIdentifier)
                .projectIdentifier(projectIdentifier)
                .status(EnvironmentUpdatedEvent.Status.DELETED)
                .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                .oldOverrideAuditEventDTO(ServiceOverrideEventDTOMapper.toOverrideAuditEventDTO(existingEntity))
                .overrideAuditV2(true)
                .build());
      } catch (IOException e) {
        throw new InvalidRequestException(
            "Failed to save event for override with identifier: " + existingEntity.getIdentifier(), e);
      }
      return true;
    }));
  }

  private void modifyRequestedServiceOverride(NGServiceOverridesEntity requestServiceOverride) {
    if (isEmpty(requestServiceOverride.getIdentifier())) {
      requestServiceOverride.setIdentifier(createOverrideIdentifier(requestServiceOverride));
    }
    requestServiceOverride.setIsV2(Boolean.TRUE);
  }

  private String createOverrideIdentifier(NGServiceOverridesEntity requestServiceOverride) {
    return overrideValidatorService.generateServiceOverrideIdentifier(requestServiceOverride);
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  private Criteria addProjectScopeCriteria(@NonNull String accountId, @NonNull String orgId, @NonNull String projectId,
      @NonNull Criteria additionalCriteria) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId)
                            .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier)
                            .is(projectId);
    return criteria.andOperator(additionalCriteria);
  }

  private Criteria addOrgScopeCriteria(
      @NonNull String accountId, @NonNull String orgId, @NonNull Criteria additionalCriteria) {
    Criteria criteria = new Criteria()
                            .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId)
                            .is(accountId)
                            .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier)
                            .is(orgId);

    return criteria.andOperator(
        new Criteria().orOperator(
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).is(null)),
        additionalCriteria);
  }

  private Criteria addAccountScopeCriteria(String accountId, Criteria additionalCriteria) {
    Criteria criteria =
        new Criteria().and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId).is(accountId);
    return criteria.andOperator(
        new Criteria().orOperator(
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier).is(null)),
        new Criteria().orOperator(
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).exists(false),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).is(null)),
        additionalCriteria);
  }

  private Map<Scope, NGServiceOverridesEntity> getScopedEntities(
      @NonNull String accountId, String orgId, String projectId, Criteria criteria, NGLogCallback logCallback) {
    Map<Scope, NGServiceOverridesEntity> scopedEntities = new HashMap<>();
    if (isNotEmpty(projectId)) {
      List<NGServiceOverridesEntity> projectScopedEntity =
          findAll(addProjectScopeCriteria(accountId, orgId, projectId, criteria));
      if (projectScopedEntity.size() > 1 && logCallback != null) {
        logCallback.saveExecutionLog(
            "Found more than one override at project scope. Only one entity will be considered at execution time",
            LogLevel.WARN);
      }
      projectScopedEntity.stream().findFirst().ifPresent(
          overrideEntity -> scopedEntities.put(Scope.PROJECT, overrideEntity));
    }

    if (isNotEmpty(orgId)) {
      List<NGServiceOverridesEntity> orgScopedEntity = findAll(addOrgScopeCriteria(accountId, orgId, criteria));
      if (orgScopedEntity.size() > 1 && logCallback != null) {
        logCallback.saveExecutionLog(
            "Found more than one override at org scope. Only one entity will be considered at execution time",
            LogLevel.WARN);
      }
      orgScopedEntity.stream().findFirst().ifPresent(overrideEntity -> scopedEntities.put(Scope.ORG, overrideEntity));
    }

    List<NGServiceOverridesEntity> accountScopedEntity = findAll(addAccountScopeCriteria(accountId, criteria));
    if (accountScopedEntity.size() > 1 && logCallback != null) {
      logCallback.saveExecutionLog(
          "Found more than one override at account scope. Only one entity will be considered at execution time",
          LogLevel.WARN);
    }
    accountScopedEntity.stream().findFirst().ifPresent(
        overrideEntity -> scopedEntities.put(Scope.ACCOUNT, overrideEntity));
    return scopedEntities;
  }

  private ServiceOverridesSpec getFinalMergedSpecFromOverridesGroupedByType(
      @NonNull List<NGServiceOverridesEntity> overridesEntities) {
    Map<String, NGVariable> finalNGVariables = new HashMap<>();
    Map<String, ManifestConfigWrapper> finalManifests = new HashMap<>();
    Map<String, ConfigFileWrapper> finalConfigFiles = new HashMap<>();
    ApplicationSettingsConfiguration finalApplicationSetting = ApplicationSettingsConfiguration.builder().build();
    ConnectionStringsConfiguration finalConnectionStrings = ConnectionStringsConfiguration.builder().build();

    Map<Scope, NGServiceOverridesEntity> overridesGroupByScope = getOverridesGroupByScope(overridesEntities);
    if (overridesGroupByScope.containsKey(Scope.ACCOUNT)) {
      ServiceOverridesSpec accOverridesSpec = overridesGroupByScope.get(Scope.ACCOUNT).getSpec();
      updateSpecFieldsByEntityFields(finalNGVariables, finalManifests, finalConfigFiles, accOverridesSpec);
      finalApplicationSetting = accOverridesSpec.getApplicationSettings();
      finalConnectionStrings = accOverridesSpec.getConnectionStrings();
    }
    if (overridesGroupByScope.containsKey(Scope.ORG)) {
      ServiceOverridesSpec orgOverridesSpec = overridesGroupByScope.get(Scope.ORG).getSpec();
      updateSpecFieldsByEntityFields(finalNGVariables, finalManifests, finalConfigFiles, orgOverridesSpec);
      finalApplicationSetting = orgOverridesSpec.getApplicationSettings();
      finalConnectionStrings = orgOverridesSpec.getConnectionStrings();
    }
    if (overridesGroupByScope.containsKey(Scope.PROJECT)) {
      ServiceOverridesSpec projectOverridesSpec = overridesGroupByScope.get(Scope.PROJECT).getSpec();
      updateSpecFieldsByEntityFields(finalNGVariables, finalManifests, finalConfigFiles, projectOverridesSpec);
      finalApplicationSetting = projectOverridesSpec.getApplicationSettings();
      finalConnectionStrings = projectOverridesSpec.getConnectionStrings();
    }

    return ServiceOverridesSpec.builder()
        .variables(new ArrayList<>(finalNGVariables.values()))
        .manifests(new ArrayList<>(finalManifests.values()))
        .configFiles(new ArrayList<>(finalConfigFiles.values()))
        .connectionStrings(finalConnectionStrings)
        .applicationSettings(finalApplicationSetting)
        .build();
  }

  private Map<Scope, NGServiceOverridesEntity> getOverridesGroupByScope(
      @NonNull List<NGServiceOverridesEntity> overridesEntities) {
    Map<Scope, NGServiceOverridesEntity> overrideGroupByScope = new HashMap<>();
    overridesEntities.forEach(entity
        -> overrideGroupByScope.put(
            ScopeHelper.getScope(entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier()),
            entity));
    return overrideGroupByScope;
  }

  private void updateSpecFieldsByEntityFields(Map<String, NGVariable> finalNGVariables,
      Map<String, ManifestConfigWrapper> finalManifests, Map<String, ConfigFileWrapper> finalConfigFiles,
      ServiceOverridesSpec overridesSpec) {
    if (isNotEmpty(overridesSpec.getVariables())) {
      overridesSpec.getVariables().forEach(ngVar -> finalNGVariables.put(ngVar.getName(), ngVar));
    }
    if (isNotEmpty(overridesSpec.getManifests())) {
      overridesSpec.getManifests().forEach(manifest -> {
        if (finalManifests.containsKey(manifest.getManifest().getIdentifier())) {
          throw new InvalidRequestException(
              "Found duplicate manifest identifier:" + manifest.getManifest().getIdentifier());
        }
        finalManifests.put(manifest.getManifest().getIdentifier(), manifest);
      });
    }
    if (isNotEmpty(overridesSpec.getConfigFiles())) {
      overridesSpec.getConfigFiles().forEach(
          configFile -> finalConfigFiles.put(configFile.getConfigFile().getIdentifier(), configFile));
    }
  }

  @Override
  public boolean deleteAllInOrg(String accountId, String orgIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgId must be present");

    return deleteAllInternal(accountId, orgIdentifier, null);
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
    DeleteResult delete = serviceOverrideRepositoryV2.delete(criteria);
    if (delete.wasAcknowledged()) {
      deleteSetupUsages(criteria);
    }
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  private void deleteSetupUsages(Criteria criteria) {
    List<NGServiceOverridesEntity> serviceOverridesEntities = findAll(criteria);
    for (NGServiceOverridesEntity serviceOverridesEntity : serviceOverridesEntities) {
      serviceOverridesV2SetupUsageHelper.deleteSetupUsages(serviceOverridesEntity);
    }
  }

  @Override
  public boolean deleteAllForEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(environmentRef), "environment ref must be present");

    Criteria criteria =
        getServiceOverrideEqualityCriteriaForEnv(accountId, orgIdentifier, projectIdentifier, environmentRef);
    DeleteResult delete = serviceOverrideRepositoryV2.delete(criteria);
    if (delete.wasAcknowledged()) {
      deleteSetupUsages(criteria);
    }
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  @Override
  public boolean deleteAllForInfra(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String infraIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(environmentRef), "environment ref must be present");
    checkArgument(isNotEmpty(infraIdentifier), "infraId must be present");

    Criteria criteria = getServiceOverrideEqualityCriteriaForInfra(
        accountId, orgIdentifier, projectIdentifier, environmentRef, infraIdentifier);
    DeleteResult delete = serviceOverrideRepositoryV2.delete(criteria);
    if (delete.wasAcknowledged()) {
      deleteSetupUsages(criteria);
    }
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  @Override
  public boolean deleteAllOfService(
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
    DeleteResult delete = serviceOverrideRepositoryV2.delete(criteria);
    if (delete.wasAcknowledged()) {
      deleteSetupUsages(criteria);
    }
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  private Criteria getServiceOverrideEqualityCriteria(String accountId, String orgId, String projId) {
    Criteria baseCriteria = Criteria.where(NGServiceOverridesEntityKeys.accountId).is(accountId);

    if (isNotEmpty(orgId)) {
      baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projId)) {
      baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).is(projId);
    }

    return baseCriteria;
  }

  private Criteria getServiceOverrideEqualityCriteriaForEnv(
      String accountId, String orgId, String projId, String envId) {
    String[] environmentRefSplit = StringUtils.split(envId, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    Criteria baseCriteria;
    final String environmentIdentifier;
    final String qualifiedEnvironmentRef;

    if (environmentRefSplit == null || environmentRefSplit.length == 1) {
      qualifiedEnvironmentRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projId, envId);
      environmentIdentifier = envId;
      baseCriteria = Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId).is(accountId);

      if (isNotEmpty(orgId)) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier).is(orgId);
      }
      if (isNotEmpty(projId)) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).is(projId);
      }

    } else {
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(envId, accountId, orgId, projId);
      qualifiedEnvironmentRef = envId;
      environmentIdentifier = envIdentifierRef.getIdentifier();
      baseCriteria = Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId)
                         .is(envIdentifierRef.getAccountIdentifier());

      if (isNotEmpty(envIdentifierRef.getOrgIdentifier())) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier)
            .is(envIdentifierRef.getOrgIdentifier());
      }
      if (isNotEmpty(envIdentifierRef.getProjectIdentifier())) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier)
            .is(envIdentifierRef.getProjectIdentifier());
      }
    }

    return baseCriteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.environmentRef)
                                      .is(qualifiedEnvironmentRef),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.environmentRef)
                .is(environmentIdentifier)));
  }

  private Criteria getServiceOverrideEqualityCriteriaForInfra(
      String accountId, String orgId, String projId, String envId, String infraId) {
    String[] environmentRefSplit = StringUtils.split(envId, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);

    Criteria baseCriteria;
    final String environmentIdentifier;
    final String qualifiedEnvironmentRef;

    if (environmentRefSplit == null || environmentRefSplit.length == 1) {
      qualifiedEnvironmentRef = IdentifierRefHelper.getRefFromIdentifierOrRef(accountId, orgId, projId, envId);
      environmentIdentifier = envId;
      baseCriteria = Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId)
                         .is(accountId)
                         .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.infraIdentifier)
                         .is(infraId);

      if (isNotEmpty(orgId)) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier).is(orgId);
      }
      if (isNotEmpty(projId)) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).is(projId);
      }

    } else {
      IdentifierRef envIdentifierRef = IdentifierRefHelper.getIdentifierRef(envId, accountId, orgId, projId);
      qualifiedEnvironmentRef = envId;
      environmentIdentifier = envIdentifierRef.getIdentifier();
      baseCriteria = Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId)
                         .is(envIdentifierRef.getAccountIdentifier())
                         .and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.infraIdentifier)
                         .is(infraId);

      if (isNotEmpty(envIdentifierRef.getOrgIdentifier())) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier)
            .is(envIdentifierRef.getOrgIdentifier());
      }
      if (isNotEmpty(envIdentifierRef.getProjectIdentifier())) {
        baseCriteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier)
            .is(envIdentifierRef.getProjectIdentifier());
      }
    }

    return baseCriteria.andOperator(new Criteria().andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.type)
                                      .is(ServiceOverridesType.INFRA_SERVICE_OVERRIDE),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.type)
                .is(ServiceOverridesType.INFRA_GLOBAL_OVERRIDE)),
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.environmentRef)
                                      .is(qualifiedEnvironmentRef),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.environmentRef)
                .is(environmentIdentifier))));
  }

  private Criteria getServiceOverrideEqualityCriteriaForServiceRef(
      String accountId, String orgId, String projId, String serviceRef) {
    Criteria criteria = new Criteria();
    criteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.accountId).is(accountId);
    if (isNotEmpty(orgId)) {
      criteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projId)) {
      criteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.projectIdentifier).is(projId);
    }
    criteria.and(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.serviceRef).is(serviceRef);
    criteria.andOperator(
        new Criteria().orOperator(Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.type)
                                      .is(ServiceOverridesType.ENV_SERVICE_OVERRIDE),
            Criteria.where(NGServiceOverridesEntity.NGServiceOverridesEntityKeys.type)
                .is(ServiceOverridesType.INFRA_SERVICE_OVERRIDE)));
    return criteria;
  }
}
