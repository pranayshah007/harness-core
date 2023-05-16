/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sidekick.VerificationTaskCleanupSideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CVConfig.CVConfigKeys;
import io.harness.cvng.core.entities.CVConfig.CVConfigUpdatableEntity;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.NextGenMetricCVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.encryption.Scope;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CVConfigServiceImpl implements CVConfigService {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Map<DataSourceType, CVConfigUpdatableEntity> dataSourceTypeCVConfigMapBinder;

  @Inject private NextGenMetricCVConfig.UpdatableEntity metricCVConfigUpdatableEntity;

  @Inject private NextGenLogCVConfig.ConfigUpdatableEntity logCVConfigUpdatableEntity;

  @Inject private SideKickService sideKickService;
  @Inject private Clock clock;

  @Override
  public CVConfig save(CVConfig cvConfig) {
    checkArgument(cvConfig.getUuid() == null, "UUID should be null when creating CVConfig");
    cvConfig.validate();
    cvConfig.setVerificationType(cvConfig.getVerificationType());
    cvConfig.setDataSourceName(cvConfig.getType());
    hPersistence.save(cvConfig);
    verificationTaskService.createLiveMonitoringVerificationTask(
        cvConfig.getAccountId(), cvConfig.getUuid(), cvConfig.getVerificationTaskTags());
    return cvConfig;
  }

  @Override
  public List<CVConfig> save(List<CVConfig> cvConfigs) {
    return cvConfigs.stream().map(this::save).collect(Collectors.toList());
  }

  @Nullable
  @Override
  public CVConfig get(@NotNull String cvConfigId) {
    return hPersistence.get(CVConfig.class, cvConfigId);
  }

  @Override
  public void update(CVConfig cvConfig) {
    checkNotNull(cvConfig.getUuid(), "Trying to update a CVConfig with empty UUID.");
    cvConfig.validate();
    UpdateOperations<CVConfig> updateOperations = hPersistence.createUpdateOperations(CVConfig.class);
    UpdatableEntity<CVConfig, CVConfig> updatableEntity = getCvConfigUpdateableEntity(cvConfig);
    Preconditions.checkNotNull(updatableEntity);
    updatableEntity.setUpdateOperations(updateOperations, cvConfig);
    hPersistence.update(get(cvConfig.getUuid()), updateOperations);
  }

  private UpdatableEntity<CVConfig, CVConfig> getCvConfigUpdateableEntity(CVConfig baseCVConfig) {
    DataSourceType type = baseCVConfig.getType();
    UpdatableEntity<? extends CVConfig, ? extends CVConfig> updatableEntity;
    if (baseCVConfig instanceof NextGenLogCVConfig) {
      updatableEntity = logCVConfigUpdatableEntity;
    } else if (baseCVConfig instanceof NextGenMetricCVConfig) {
      updatableEntity = metricCVConfigUpdatableEntity;
    } else {
      updatableEntity = dataSourceTypeCVConfigMapBinder.get(type);
    }
    return (UpdatableEntity<CVConfig, CVConfig>) updatableEntity;
  }

  @Override
  public void update(List<CVConfig> cvConfigs) {
    cvConfigs.forEach(cvConfig -> cvConfig.validate());
    cvConfigs.forEach(this::update); // TODO: implement batch update
  }

  @Override
  public void delete(@NotNull String cvConfigId) {
    delete(cvConfigId, clock.instant().plus(Duration.ofHours(2)));
  }

  @Override
  public void deleteImmediately(@NotNull String cvConfigId) {
    delete(cvConfigId, clock.instant());
  }

  private void delete(String cvConfigId, Instant deleteAfter) {
    CVConfig cvConfig = get(cvConfigId);
    if (cvConfig == null) {
      return;
    }
    String verificationTaskId =
        verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfig.getUuid());
    sideKickService.schedule(
        VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskId).cvConfig(cvConfig).build(),
        deleteAfter);
    hPersistence.delete(CVConfig.class, cvConfigId);
  }

  @Override
  public void deleteByIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier) {
    try (
        HIterator<CVConfig> cvConfigs = new HIterator<>(hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                                            .filter(CVConfigKeys.accountId, accountId)
                                                            .filter(CVConfigKeys.orgIdentifier, orgIdentifier)
                                                            .filter(CVConfigKeys.projectIdentifier, projectIdentifier)
                                                            .filter(CVConfigKeys.identifier, monitoringSourceIdentifier)
                                                            .fetch())) {
      for (CVConfig cvConfig : cvConfigs) {
        delete(cvConfig.getUuid());
      }
    }
  }

  @Override
  public List<CVConfig> findByConnectorIdentifier(String accountId, String orgIdentifier, String projectIdentifier,
      String connectorIdentifierWithoutScopePrefix, Scope scope) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(connectorIdentifierWithoutScopePrefix);
    String connectorIdentifier = connectorIdentifierWithoutScopePrefix;
    if (scope == Scope.ACCOUNT || scope == Scope.ORG) {
      connectorIdentifier = scope.getYamlRepresentation() + "." + connectorIdentifierWithoutScopePrefix;
    }
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.connectorIdentifier, connectorIdentifier);
    if (scope == Scope.ORG) {
      query = query.filter(CVConfigKeys.orgIdentifier, orgIdentifier);
    }
    if (scope == Scope.PROJECT) {
      query = query.filter(CVConfigKeys.projectIdentifier, projectIdentifier);
    }
    return query.asList();
  }

  @Override
  public List<CVConfig> list(@NotNull String accountId, String connectorIdentifier) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.connectorIdentifier, connectorIdentifier);
    return query.asList();
  }
  @Override
  public List<CVConfig> list(String accountId, String connectorIdentifier, String productName) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.connectorIdentifier, connectorIdentifier)
                                .filter(CVConfigKeys.productName, productName);
    return query.asList();
  }
  @Override
  public List<CVConfig> list(
      String accountId, String orgIdentifier, String projectIdentifier, String monitoringSourceIdentifier) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                .filter(CVConfigKeys.accountId, accountId)
                                .filter(CVConfigKeys.orgIdentifier, orgIdentifier)
                                .filter(CVConfigKeys.projectIdentifier, projectIdentifier)
                                .filter(CVConfigKeys.identifier, monitoringSourceIdentifier);
    return query.asList();
  }

  @Override
  public List<String> getProductNames(String accountId, String connectorIdentifier) {
    checkNotNull(accountId, "accountId can not be null");
    checkNotNull(connectorIdentifier, "ConnectorIdentifier can not be null");
    return hPersistence.createQuery(CVConfig.class, excludeAuthority)
        .filter(CVConfigKeys.connectorIdentifier, connectorIdentifier)
        .filter(CVConfigKeys.accountId, accountId)
        .project(CVConfigKeys.productName, true)
        .asList()
        .stream()
        .map(cvConfig -> cvConfig.getProductName())
        .distinct()
        .sorted()
        .collect(toList());
  }

  @Override
  public List<CVConfig> list(MonitoredServiceParams monitoredServiceParams) {
    return createQuery(monitoredServiceParams).asList();
  }

  @Override
  public List<CVConfig> list(MonitoredServiceParams monitoredServiceParams, List<String> identifiers) {
    Query<CVConfig> query = createQuery(monitoredServiceParams);
    query.field(CVConfigKeys.identifier).in(identifiers);
    return query.asList();
  }

  @Override
  public List<CVConfig> list(ProjectParams projectParams, List<String> identifiers) {
    return hPersistence.createQuery(CVConfig.class, excludeAuthority)
        .filter(CVConfigKeys.accountId, projectParams.getAccountIdentifier())
        .filter(CVConfigKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(CVConfigKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .field(CVConfigKeys.identifier)
        .in(identifiers)
        .asList();
  }

  @Override
  public Map<String, DataSourceType> getDataSourceTypeForCVConfigs(MonitoredServiceParams monitoredServiceParams) {
    Map<String, DataSourceType> cvConfigIdDataSourceTypeMap = new HashMap<>();
    Query<CVConfig> query = createQuery(monitoredServiceParams);
    query.asList().forEach(cvConfig -> cvConfigIdDataSourceTypeMap.put(cvConfig.getUuid(), cvConfig.getType()));
    return cvConfigIdDataSourceTypeMap;
  }

  @Override
  public List<String> getMonitoringSourceIds(
      String accountId, String orgIdentifier, String projectIdentifier, String filter) {
    BasicDBObject cvConfigQuery = new BasicDBObject();
    List<BasicDBObject> conditions = new ArrayList<>();
    conditions.add(new BasicDBObject(CVConfigKeys.accountId, accountId));
    conditions.add(new BasicDBObject(CVConfigKeys.projectIdentifier, projectIdentifier));
    conditions.add(new BasicDBObject(CVConfigKeys.orgIdentifier, orgIdentifier));
    cvConfigQuery.put("$and", conditions);
    List<String> allMonitoringSourceIds =
        hPersistence.getCollection(CVConfig.class).distinct(CVConfigKeys.identifier, cvConfigQuery);
    Collections.reverse(allMonitoringSourceIds);
    if (isEmpty(allMonitoringSourceIds) || isEmpty(filter)) {
      return allMonitoringSourceIds;
    }

    return allMonitoringSourceIds.stream()
        .filter(identifier -> identifier.toLowerCase().contains(filter.trim().toLowerCase()))
        .collect(toList());
  }

  @Override
  public List<CVConfig> listByMonitoringSources(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> monitoringSourceIdentifier) {
    return hPersistence.createQuery(CVConfig.class, excludeAuthority)
        .filter(CVConfigKeys.accountId, accountId)
        .filter(CVConfigKeys.orgIdentifier, orgIdentifier)
        .filter(CVConfigKeys.projectIdentifier, projectIdentifier)
        .field(CVConfigKeys.identifier)
        .in(monitoringSourceIdentifier)
        .asList();
  }

  @Override
  public boolean doesAnyCVConfigExistsInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    long numberOfCVConfigs = hPersistence.createQuery(CVConfig.class, excludeAuthority)
                                 .filter(CVConfigKeys.accountId, accountId)
                                 .filter(CVConfigKeys.orgIdentifier, orgIdentifier)
                                 .filter(CVConfigKeys.projectIdentifier, projectIdentifier)
                                 .count();
    return numberOfCVConfigs > 0;
  }

  private Query createQuery(MonitoredServiceParams monitoredServiceParams) {
    return hPersistence.createQuery(CVConfig.class, excludeAuthority)
        .filter(CVConfigKeys.accountId, monitoredServiceParams.getAccountIdentifier())
        .filter(CVConfigKeys.orgIdentifier, monitoredServiceParams.getOrgIdentifier())
        .filter(CVConfigKeys.projectIdentifier, monitoredServiceParams.getProjectIdentifier())
        .filter(CVConfigKeys.monitoredServiceIdentifier, monitoredServiceParams.getMonitoredServiceIdentifier());
  }

  private void deleteConfigsForEntity(
      String accountId, @Nullable String orgIdentifier, @Nullable String projectIdentifier) {
    Query<CVConfig> query = hPersistence.createQuery(CVConfig.class);
    query = query.filter(CVConfigKeys.accountId, accountId);

    if (orgIdentifier != null) {
      query = query.filter(CVConfigKeys.orgIdentifier, orgIdentifier);
    }

    if (projectIdentifier != null) {
      query = query.filter(CVConfigKeys.projectIdentifier, projectIdentifier);
    }

    List<CVConfig> cvConfigs = query.asList();
    cvConfigs.forEach(cvConfig -> delete(cvConfig.getUuid()));
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<CVConfig> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    Preconditions.checkState(clazz.equals(CVConfig.class), "Class should be of type CVConfig");
    this.deleteConfigsForEntity(accountId, orgIdentifier, projectIdentifier);
  }

  @Override
  public void deleteByOrgIdentifier(Class<CVConfig> clazz, String accountId, String orgIdentifier) {
    Preconditions.checkState(clazz.equals(CVConfig.class), "Class should be of type CVConfig");
    this.deleteConfigsForEntity(accountId, orgIdentifier, null);
  }

  @Override
  public void deleteByAccountIdentifier(Class<CVConfig> clazz, String accountId) {
    Preconditions.checkState(clazz.equals(CVConfig.class), "Class should be of type CVConfig");
    this.deleteConfigsForEntity(accountId, null, null);
  }

  @Override
  public void setHealthMonitoringFlag(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> identifiers, boolean isEnabled) {
    hPersistence.update(hPersistence.createQuery(CVConfig.class)
                            .filter(CVConfigKeys.accountId, accountId)
                            .filter(CVConfigKeys.orgIdentifier, orgIdentifier)
                            .filter(CVConfigKeys.projectIdentifier, projectIdentifier)
                            .field(CVConfigKeys.identifier)
                            .in(identifiers),
        hPersistence.createUpdateOperations(CVConfig.class).set(CVConfigKeys.enabled, isEnabled));
  }

  @Override
  public List<CVConfig> listByMonitoringSources(
      MonitoredServiceParams monitoredServiceParams, List<String> healthSourceIdentifiers) {
    List<CVConfig> cvConfigs = createQuery(monitoredServiceParams).asList();
    if (healthSourceIdentifiers == null) {
      return cvConfigs;
    }
    return cvConfigs.stream()
        .filter(cvConfig -> healthSourceIdentifiers.contains(cvConfig.getIdentifier()))
        .collect(Collectors.toList());
  }

  @Override
  public List<CVConfig> getCVConfigs(MonitoredServiceParams monitoredServiceParams) {
    return hPersistence.createQuery(CVConfig.class, excludeAuthority)
        .filter(CVConfigKeys.accountId, monitoredServiceParams.getAccountIdentifier())
        .filter(CVConfigKeys.orgIdentifier, monitoredServiceParams.getOrgIdentifier())
        .filter(CVConfigKeys.projectIdentifier, monitoredServiceParams.getProjectIdentifier())
        .filter(CVConfigKeys.monitoredServiceIdentifier, monitoredServiceParams.getMonitoredServiceIdentifier())
        .asList();
  }

  @Override
  public List<CVConfig> getCVConfigs(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(CVConfig.class, excludeAuthority)
        .filter(CVConfigKeys.accountId, projectParams.getAccountIdentifier())
        .filter(CVConfigKeys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(CVConfigKeys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(CVConfigKeys.identifier, identifier)
        .asList();
  }
}
