/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serviceoverridesv2.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.serviceoverridesv2.validators.ServiceOverrideValidatorService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.serviceoverridesv2.custom.ServiceOverrideRepositoryHelper;
import io.harness.repositories.serviceoverridesv2.spring.ServiceOverridesRepositoryV2;
import io.harness.yaml.core.variables.NGVariable;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceOverridesServiceV2Impl implements ServiceOverridesServiceV2 {
  private final ServiceOverridesRepositoryV2 serviceOverrideRepositoryV2;
  private final OutboxService outboxService;
  private final ServiceOverrideValidatorService overrideValidatorService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;

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
  public Optional<NGServiceOverridesEntity> get(@NotNull String accountId, String orgIdentifier,
      String projectIdentifier, @NonNull String serviceOverridesIdentifier) {
    return serviceOverrideRepositoryV2
        .getNGServiceOverridesEntityByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountId, orgIdentifier, projectIdentifier, serviceOverridesIdentifier);
  }

  @Override
  public NGServiceOverridesEntity create(@NonNull NGServiceOverridesEntity requestedEntity) {
    validatePresenceOfRequiredFields(
        requestedEntity.getAccountId(), requestedEntity.getEnvironmentRef(), requestedEntity.getType());
    modifyRequestedServiceOverride(requestedEntity);
    Optional<NGServiceOverridesEntity> existingEntity =
        serviceOverrideRepositoryV2
            .getNGServiceOverridesEntityByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
                requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(),
                requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());
    if (existingEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Service Override with identifier [%s] already exists", requestedEntity.getIdentifier()));
    }

    return Failsafe.with(transactionRetryPolicy)
        .get(() -> transactionTemplate.execute(status -> saveAndSendOutBoxEvent(requestedEntity)));
  }

  @Override
  public NGServiceOverridesEntity update(@NonNull @Valid NGServiceOverridesEntity requestedEntity) {
    validatePresenceOfRequiredFields(
        requestedEntity.getAccountId(), requestedEntity.getEnvironmentRef(), requestedEntity.getType());
    modifyRequestedServiceOverride(requestedEntity);
    Criteria equalityCriteria = ServiceOverrideRepositoryHelper.getEqualityCriteriaForServiceOverride(
        requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
        requestedEntity.getIdentifier());
    Optional<NGServiceOverridesEntity> existingEntityInDb = get(requestedEntity.getAccountId(),
        requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());

    if (existingEntityInDb.isPresent()) {
      overrideValidatorService.checkForImmutablePropertiesOrThrow(existingEntityInDb.get(), requestedEntity);

      return Failsafe.with(transactionRetryPolicy)
          .get(
              ()
                  -> transactionTemplate.execute(
                      status -> updateAndSendOutboxEvent(requestedEntity, equalityCriteria, existingEntityInDb.get())));
    } else {
      throw new InvalidRequestException(String.format(
          "ServiceOverride [%s] under Project[%s], Organization [%s] doesn't exist.", requestedEntity.getIdentifier(),
          requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier()));
    }
  }

  @Override
  public boolean delete(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String identifier, NGServiceOverridesEntity existingEntity) {
    if (existingEntity == null) {
      existingEntity = checkIfServiceOverrideExistAndThrow(accountId, orgIdentifier, projectIdentifier, identifier);
    }

    return deleteInternal(accountId, orgIdentifier, projectIdentifier, identifier, existingEntity);
  }

  @Override
  public Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageRequest) {
    return serviceOverrideRepositoryV2.findAll(criteria, pageRequest);
  }

  @Override
  public Pair<NGServiceOverridesEntity, Boolean> upsert(NGServiceOverridesEntity requestedEntity) {
    validatePresenceOfRequiredFields(
        requestedEntity.getAccountId(), requestedEntity.getEnvironmentRef(), requestedEntity.getType());
    modifyRequestedServiceOverride(requestedEntity);
    Criteria equalityCriteria = ServiceOverrideRepositoryHelper.getEqualityCriteriaForServiceOverride(
        requestedEntity.getAccountId(), requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(),
        requestedEntity.getIdentifier());
    Optional<NGServiceOverridesEntity> existingEntityInDb = get(requestedEntity.getAccountId(),
        requestedEntity.getOrgIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getIdentifier());

    if (existingEntityInDb.isEmpty()) {
      NGServiceOverridesEntity createdOverrideEntity =
          Failsafe.with(transactionRetryPolicy)
              .get(() -> transactionTemplate.execute(status -> saveAndSendOutBoxEvent(requestedEntity)));
      return new ImmutablePair<>(createdOverrideEntity, true);
    }

    overrideValidatorService.checkForImmutablePropertiesOrThrow(existingEntityInDb.get(), requestedEntity);
    NGServiceOverridesEntity mergedOverrideEntity =
        mergeRequestedAndExistingOverrides(requestedEntity, existingEntityInDb.get());

    NGServiceOverridesEntity updateOverrideEntity =
        Failsafe.with(transactionRetryPolicy)
            .get(
                ()
                    -> transactionTemplate.execute(status
                        -> updateAndSendOutboxEvent(mergedOverrideEntity, equalityCriteria, existingEntityInDb.get())));
    return new ImmutablePair<>(updateOverrideEntity, false);
  }

  private NGServiceOverridesEntity saveAndSendOutBoxEvent(@NotNull NGServiceOverridesEntity requestedEntity) {
    NGServiceOverridesEntity tempCreateResult = serviceOverrideRepositoryV2.save(requestedEntity);
    if (tempCreateResult == null) {
      throw new InvalidRequestException(String.format(
          "NGServiceOverridesEntity under Project[%s], Organization [%s], Environment [%s] and Service [%s] couldn't be created.",
          requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier(),
          requestedEntity.getEnvironmentRef(), requestedEntity.getServiceRef()));
    }

    outboxService.save(EnvironmentUpdatedEvent.builder()
                           .accountIdentifier(tempCreateResult.getAccountId())
                           .orgIdentifier(tempCreateResult.getOrgIdentifier())
                           .status(EnvironmentUpdatedEvent.Status.CREATED)
                           .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                           .projectIdentifier(tempCreateResult.getProjectIdentifier())
                           .newServiceOverridesEntity(tempCreateResult)
                           .build());

    return tempCreateResult;
  }

  private NGServiceOverridesEntity updateAndSendOutboxEvent(@NotNull NGServiceOverridesEntity requestedEntity,
      Criteria equalityCriteria, NGServiceOverridesEntity existingEntityInDb) {
    NGServiceOverridesEntity updatedServiceOverride =
        serviceOverrideRepositoryV2.update(equalityCriteria, requestedEntity);
    if (updatedServiceOverride == null) {
      throw new InvalidRequestException(String.format(
          "ServiceOverride [%s] under Project [%s], Organization [%s] couldn't be updated or doesn't exist.",
          requestedEntity.getIdentifier(), requestedEntity.getProjectIdentifier(), requestedEntity.getOrgIdentifier()));
    }
    outboxService.save(EnvironmentUpdatedEvent.builder()
                           .accountIdentifier(updatedServiceOverride.getAccountId())
                           .orgIdentifier(updatedServiceOverride.getOrgIdentifier())
                           .projectIdentifier(updatedServiceOverride.getProjectIdentifier())
                           .newServiceOverridesEntity(updatedServiceOverride)
                           .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                           .status(EnvironmentUpdatedEvent.Status.UPDATED)
                           .oldServiceOverridesEntity(existingEntityInDb)
                           .build());
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
        .configFiles(mergeConfigFiles(requestedSpec.getConfigFiles(), requestedSpec.getConfigFiles()))
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
      @NotNull String accountId, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    Optional<NGServiceOverridesEntity> existingOverrideInDb =
        serviceOverrideRepositoryV2
            .getNGServiceOverridesEntityByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
                accountId, orgIdentifier, projectIdentifier, identifier);
    if (existingOverrideInDb.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Service Override with identifier: [%s], projectId: [%s], orgId: [%s] does not exist",
              identifier, projectIdentifier, orgIdentifier));
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
            String.format("Service Override [%s], Project[%s], Organization [%s] couldn't be deleted.", identifier,
                projectIdentifier, orgIdentifier));
      }
      outboxService.save(EnvironmentUpdatedEvent.builder()
                             .accountIdentifier(accountId)
                             .orgIdentifier(orgIdentifier)
                             .projectIdentifier(projectIdentifier)
                             .status(EnvironmentUpdatedEvent.Status.DELETED)
                             .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                             .oldServiceOverridesEntity(existingEntity)
                             .build());
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
}
