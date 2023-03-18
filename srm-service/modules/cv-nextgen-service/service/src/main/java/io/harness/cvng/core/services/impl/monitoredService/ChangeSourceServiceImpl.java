/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.monitoredService;

import static io.harness.cvng.core.utils.FeatureFlagNames.CVNG_MONITORED_SERVICE_DEMO;

import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionType;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.HarnessCDCurrentGenEventMetadata;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.sidekick.RetryChangeSourceHandleDeleteSideKickData;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.ChangeSource.ChangeSourceKeys;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.services.impl.ChangeSourceUpdateHandler;
import io.harness.cvng.core.transformer.changeSource.ChangeSourceEntityAndDTOTransformer;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public class ChangeSourceServiceImpl implements ChangeSourceService {
  @Inject private HPersistence hPersistence;
  @Inject private ChangeSourceEntityAndDTOTransformer changeSourceTransformer;
  @Inject private ChangeEventService changeEventService;
  @Inject private Map<ChangeSourceType, ChangeSourceUpdateHandler> changeSourceUpdateHandlerMap;
  @Inject private Map<ChangeSourceType, ChangeSource.UpdatableChangeSourceEntity> changeSourceUpdatableMap;
  @Inject private VerificationManagerService verificationManagerService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private Map<ChangeSourceType, ChangeSourceDemoDataGenerator> changeSourceTypeToDemoDataGeneratorMap;
  @Inject private SideKickService sideKickService;

  @Override
  public void create(
      @NonNull MonitoredServiceParams monitoredServiceParams, @NonNull Set<ChangeSourceDTO> changeSourceDTOs) {
    validate(changeSourceDTOs);
    validateChangeSourcesDoesntExist(monitoredServiceParams, changeSourceDTOs);
    List<ChangeSource> changeSources = changeSourceDTOs.stream()
                                           .map(dto -> changeSourceTransformer.getEntity(monitoredServiceParams, dto))
                                           .collect(Collectors.toList());
    create(changeSources);
  }
  private void create(List<ChangeSource> changeSources) {
    changeSources.forEach(changeSource -> setConfigForDemoIfApplicable(changeSource));

    hPersistence.save(changeSources);
    changeSources.stream()
        .filter(changeSource -> changeSourceUpdateHandlerMap.containsKey(changeSource.getType()))
        .forEach(changeSource -> changeSourceUpdateHandlerMap.get(changeSource.getType()).handleCreate(changeSource));
  }

  private void setConfigForDemoIfApplicable(ChangeSource changeSource) {
    if (changeSource.isEligibleForDemo()
        && featureFlagService.isFeatureFlagEnabled(changeSource.getAccountId(), CVNG_MONITORED_SERVICE_DEMO)) {
      changeSource.setConfiguredForDemo(true);
    }
  }

  @Override
  public Set<ChangeSourceDTO> get(MonitoredServiceParams monitoredServiceParams, List<String> identifiers) {
    if (CollectionUtils.isEmpty(identifiers)) {
      return Collections.emptySet();
    }
    return createQuery(monitoredServiceParams)
        .field(ChangeSourceKeys.identifier)
        .in(identifiers)
        .asList()
        .stream()
        .map(changeSourceTransformer::getDto)
        .collect(Collectors.toSet());
  }

  @Override
  public ChangeSource get(MonitoredServiceParams monitoredServiceParams, String identifier) {
    return createQuery(monitoredServiceParams).filter(ChangeSourceKeys.identifier, identifier).get();
  }

  @Override
  public List<ChangeSource> getEntityByType(
      MonitoredServiceParams monitoredServiceParams, ChangeSourceType changeSourceType) {
    return createQuery(monitoredServiceParams).filter(ChangeSourceKeys.type, changeSourceType).asList();
  }

  @Override
  public void delete(@NonNull MonitoredServiceParams monitoredServiceParams, @NonNull List<String> identifiers) {
    List<ChangeSource> changeSources =
        createQuery(monitoredServiceParams).field(ChangeSourceKeys.identifier).in(identifiers).asList();
    changeSources.forEach(changeSource -> {
      hPersistence.delete(changeSource);
      if (changeSourceUpdateHandlerMap.containsKey(changeSource.getType())) {
        CompletableFuture.runAsync(() -> asyncChangeSourceHandleDelete(changeSource));
      }
    });
  }

  private void asyncChangeSourceHandleDelete(ChangeSource changeSource) {
    sideKickService.schedule(
        RetryChangeSourceHandleDeleteSideKickData.builder().changeSource(changeSource).build(), Instant.now());
  }

  @Override
  public void update(
      @NonNull MonitoredServiceParams monitoredServiceParams, @NonNull Set<ChangeSourceDTO> changeSourceDTOs) {
    validate(changeSourceDTOs);
    Map<String, ChangeSource> newChangeSourceMap =
        changeSourceDTOs.stream()
            .map(dto -> changeSourceTransformer.getEntity(monitoredServiceParams, dto))
            .collect(Collectors.toMap(cs -> cs.getIdentifier(), Function.identity()));

    Map<String, ChangeSource> existingChangeSourceMap =
        createQuery(monitoredServiceParams)
            .asList()
            .stream()
            .collect(Collectors.toMap(sc -> sc.getIdentifier(), Function.identity()));
    List<ChangeSource> changeSourcesToCreate = new ArrayList<>();
    newChangeSourceMap.forEach((identifier, changeSource) -> {
      if (replaceable(identifier, newChangeSourceMap, existingChangeSourceMap)) {
        ChangeSource existingChangeSource = existingChangeSourceMap.remove(identifier);
        update(existingChangeSource, changeSource);
        if (changeSourceUpdateHandlerMap.containsKey(changeSource.getType())) {
          changeSourceUpdateHandlerMap.get(changeSource.getType()).handleUpdate(existingChangeSource, changeSource);
        }
      } else {
        changeSourcesToCreate.add(changeSource);
      }
    });
    create(changeSourcesToCreate);
    existingChangeSourceMap.keySet().forEach(identifier -> {
      ChangeSource changeSource = existingChangeSourceMap.get(identifier);
      hPersistence.delete(changeSource);
      if (changeSourceUpdateHandlerMap.containsKey(changeSource.getType())) {
        changeSourceUpdateHandlerMap.get(changeSource.getType()).handleDelete(changeSource);
      }
    });
  }

  protected void update(ChangeSource existingChangeSource, ChangeSource newChangeSource) {
    UpdateOperations<ChangeSource> updateOperations = hPersistence.createUpdateOperations(ChangeSource.class);
    changeSourceUpdatableMap.get(newChangeSource.getType()).setUpdateOperations(updateOperations, newChangeSource);
    hPersistence.update(
        hPersistence.createQuery(ChangeSource.class).filter(ChangeSourceKeys.uuid, existingChangeSource.getUuid()),
        updateOperations);
  }

  @Override
  public void enqueueDataCollectionTask(KubernetesChangeSource changeSource) {
    DataCollectionConnectorBundle dataCollectionConnectorBundle =
        DataCollectionConnectorBundle.builder()
            .dataCollectionType(DataCollectionType.KUBERNETES)
            .connectorIdentifier(changeSource.getConnectorIdentifier())
            .sourceIdentifier(changeSource.getIdentifier())
            .dataCollectionWorkerId(changeSource.getUuid())
            .projectIdentifier(changeSource.getProjectIdentifier())
            .orgIdentifier(changeSource.getOrgIdentifier())
            .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
            .build();

    String dataCollectionTaskId = verificationManagerService.createDataCollectionTask(changeSource.getAccountId(),
        changeSource.getOrgIdentifier(), changeSource.getProjectIdentifier(), dataCollectionConnectorBundle);

    UpdateOperations<ChangeSource> updateOperations =
        hPersistence.createUpdateOperations(ChangeSource.class)
            .set(ChangeSourceKeys.dataCollectionTaskId, dataCollectionTaskId);
    Query<ChangeSource> query =
        hPersistence.createQuery(ChangeSource.class).filter(ChangeSourceKeys.uuid, changeSource.getUuid());
    hPersistence.update(query, updateOperations);
  }

  @Override
  public ChangeSummaryDTO getChangeSummary(MonitoredServiceParams monitoredServiceParams,
      List<String> changeSourceIdentifiers, Instant startTime, Instant endTime) {
    return changeEventService.getChangeSummary(monitoredServiceParams, changeSourceIdentifiers, startTime, endTime);
  }

  private void validate(Set<ChangeSourceDTO> changeSourceDTOs) {
    Optional<String> noUniqueIdentifier =
        changeSourceDTOs.stream()
            .map(dto -> dto.getIdentifier())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entrySet()
            .stream()
            .filter(element -> element.getValue() > 1)
            .map(entrySet -> entrySet.getKey())
            .findAny();
    if (noUniqueIdentifier.isPresent()) {
      throw new InvalidRequestException(
          String.format("Multiple Change Sources exists with the same identifier %s", noUniqueIdentifier.get()));
    }

    for (ChangeSourceDTO changeSourceDTO : changeSourceDTOs) {
      if (!changeSourceDTO.getSpec().getType().equals(changeSourceDTO.getType())) {
        throw new InvalidRequestException(String.format(
            "Invalid Change Category for change source with identifier %s", changeSourceDTO.getIdentifier()));
      }
    }
  }

  private Query<ChangeSource> createQuery(MonitoredServiceParams monitoredServiceParams) {
    return hPersistence.createQuery(ChangeSource.class)
        .filter(ChangeSourceKeys.accountId, monitoredServiceParams.getAccountIdentifier())
        .filter(ChangeSourceKeys.orgIdentifier, monitoredServiceParams.getOrgIdentifier())
        .filter(ChangeSourceKeys.projectIdentifier, monitoredServiceParams.getProjectIdentifier())
        .filter(ChangeSourceKeys.monitoredServiceIdentifier, monitoredServiceParams.getMonitoredServiceIdentifier());
  }

  private void validateChangeSourcesDoesntExist(
      MonitoredServiceParams monitoredServiceParams, Set<ChangeSourceDTO> changeSourceDTOs) {
    Set<ChangeSourceDTO> changeSourceDTOS = get(monitoredServiceParams,
        changeSourceDTOs.stream().map(changeSourceDTO -> changeSourceDTO.getIdentifier()).collect(Collectors.toList()));

    if (CollectionUtils.isNotEmpty(changeSourceDTOS)) {
      throw new InvalidRequestException(String.format("Multiple Change Sources exists with the same identifier %s",
          changeSourceDTOS.iterator().next().getIdentifier()));
    }
  }

  private boolean replaceable(
      String key, Map<String, ChangeSource> newChangeSourceMap, Map<String, ChangeSource> existingChangeSourceMap) {
    return existingChangeSourceMap.containsKey(key) && newChangeSourceMap.containsKey(key)
        && existingChangeSourceMap.get(key).getClass().equals(newChangeSourceMap.get(key).getClass());
  }

  @Override
  public void handleCurrentGenEvents(HarnessCDCurrentGenChangeSource changeSource) {
    List<HarnessCDCurrentGenEventMetadata> events = verificationManagerService.getCurrentGenEvents(
        changeSource.getAccountId(), changeSource.getHarnessApplicationId(), changeSource.getHarnessEnvironmentId(),
        changeSource.getHarnessServiceId(), Instant.now().minus(5, ChronoUnit.MINUTES), Instant.now());
    events.forEach(event -> {
      ChangeEventDTO changeEventDTO = ChangeEventDTO.builder()
                                          .accountId(changeSource.getAccountId())
                                          .orgIdentifier(changeSource.getOrgIdentifier())
                                          .projectIdentifier(changeSource.getProjectIdentifier())
                                          .changeSourceIdentifier(changeSource.getIdentifier())
                                          .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
                                          .type(ChangeSourceType.HARNESS_CD_CURRENT_GEN)
                                          .eventTime(event.getWorkflowStartTime())
                                          .metadata(event)
                                          .build();
      changeEventService.register(changeEventDTO);
    });
  }
  @Override
  public void generateDemoData(ChangeSource entity) {
    if (changeSourceTypeToDemoDataGeneratorMap.containsKey(entity.getType())) {
      if (entity.shouldGenerateAutoDemoEvents()) {
        List<ChangeEventDTO> changeEvents =
            changeSourceTypeToDemoDataGeneratorMap.get(entity.getType()).generate(entity);
        changeEvents.forEach(changeEvent -> changeEventService.register(changeEvent));
      }
    }
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<ChangeSource> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<ChangeSource> changeSources = hPersistence.createQuery(ChangeSource.class)
                                           .filter(ChangeSourceKeys.accountId, accountId)
                                           .filter(ChangeSourceKeys.orgIdentifier, orgIdentifier)
                                           .filter(ChangeSourceKeys.projectIdentifier, projectIdentifier)
                                           .asList();

    changeSources.forEach(changeSource
        -> delete(MonitoredServiceParams.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
                      .build(),
            Collections.singletonList(changeSource.getIdentifier())));
  }

  @Override
  public void deleteByOrgIdentifier(Class<ChangeSource> clazz, String accountId, String orgIdentifier) {
    List<ChangeSource> changeSources = hPersistence.createQuery(ChangeSource.class)
                                           .filter(ChangeSourceKeys.accountId, accountId)
                                           .filter(ChangeSourceKeys.orgIdentifier, orgIdentifier)
                                           .asList();

    changeSources.forEach(changeSource
        -> delete(MonitoredServiceParams.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(changeSource.getProjectIdentifier())
                      .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
                      .build(),
            Collections.singletonList(changeSource.getIdentifier())));
  }

  @Override
  public void deleteByAccountIdentifier(Class<ChangeSource> clazz, String accountId) {
    List<ChangeSource> changeSources =
        hPersistence.createQuery(ChangeSource.class).filter(ChangeSourceKeys.accountId, accountId).asList();

    changeSources.forEach(changeSource
        -> delete(MonitoredServiceParams.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(changeSource.getOrgIdentifier())
                      .projectIdentifier(changeSource.getProjectIdentifier())
                      .monitoredServiceIdentifier(changeSource.getMonitoredServiceIdentifier())
                      .build(),
            Collections.singletonList(changeSource.getIdentifier())));
  }
}
