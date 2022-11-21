/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.cluster;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.InfrastructureType.AWS_ECS;
import static software.wings.beans.InfrastructureType.AZURE_KUBERNETES;
import static software.wings.beans.InfrastructureType.DIRECT_KUBERNETES;
import static software.wings.beans.InfrastructureType.GCP_KUBERNETES_ENGINE;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.cluster.dao.ClusterRecordDao;
import io.harness.ccm.cluster.entities.AzureKubernetesCluster;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.cluster.entities.EcsCluster;
import io.harness.ccm.cluster.entities.GcpKubernetesCluster;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.reflection.ReflectionUtils;

import software.wings.beans.Application;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.SettingAttribute;
import software.wings.infra.AwsEcsInfrastructure;
import software.wings.infra.AzureKubernetesService;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ClusterRecordServiceImpl implements ClusterRecordService {
  @Inject private ClusterRecordDao clusterRecordDao;
  @Inject private AppService appService;
  @Inject private SettingsService settingsService;
  @Inject @Getter private Subject<ClusterRecordObserver> subject = new Subject<>();
  @Inject private RemoteObserverInformer remoteObserverInformer;

  @Override
  public ClusterRecord upsert(ClusterRecord clusterRecord) {
    ClusterRecord prevClusterRecord = clusterRecordDao.get(clusterRecord);
    ClusterRecord upsertedClusterRecord = clusterRecordDao.upsertCluster(clusterRecord);

    if (!isNull(prevClusterRecord)) {
      log.info("Updated the existing {} Cluster with id={}.", upsertedClusterRecord.getCluster().getClusterType(),
          upsertedClusterRecord.getUuid());
    } else {
      log.info("Upserted a new {} Cluster with id={}.", upsertedClusterRecord.getCluster().getClusterType(),
          upsertedClusterRecord.getUuid());
    }
    try {
      subject.fireInform(ClusterRecordObserver::onUpserted, upsertedClusterRecord);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ClusterRecordObserver.class, "onUpserted", ClusterRecord.class),
          ClusterRecordServiceImpl.class, upsertedClusterRecord);
    } catch (Exception e) {
      log.error("Failed to inform the observers for the Cluster with id={}", upsertedClusterRecord.getUuid(), e);
    }
    return upsertedClusterRecord;
  }

  @Override
  public ClusterRecord get(String clusterId) {
    return clusterRecordDao.get(clusterId);
  }

  @Override
  public List<ClusterRecord> list(String accountId, String clusterType) {
    return list(accountId, clusterType, null);
  }

  @Override
  public List<ClusterRecord> list(String accountId, String clusterType, String cloudProviderId) {
    List<ClusterRecord> clusterRecords = list(accountId, cloudProviderId, false);
    if (isNotEmpty(clusterType)) {
      clusterRecords = clusterRecords.stream()
                           .filter(clusterRecord -> clusterRecord.getCluster().getClusterType().equals(clusterType))
                           .collect(Collectors.toList());
    }
    return clusterRecords;
  }

  @Override
  public List<ClusterRecord> list(String accountId, String cloudProviderId, boolean isDeactivated) {
    return list(accountId, cloudProviderId, isDeactivated, 0, 0);
  }

  @Override
  public List<ClusterRecord> list(
      String accountId, String cloudProviderId, boolean isActive, Integer count, Integer startIndex) {
    return clusterRecordDao.list(accountId, cloudProviderId, count, startIndex);
  }

  public ClusterRecord deactivate(String accountId, String cloudProviderId) {
    // get the list of Clusters associated with the cloudProvider
    List<ClusterRecord> clusterRecords = list(accountId, cloudProviderId, false);
    if (isNull(clusterRecords)) {
      log.warn("Cloud Provider with id={} has no Clusters to be deactivated.", cloudProviderId);
    } else {
      for (ClusterRecord clusterRecord : clusterRecords) {
        try {
          // Both subject and remote Observer are needed since in few places DMS might not be present
          subject.fireInform(ClusterRecordObserver::onDeactivating, clusterRecord);
          remoteObserverInformer.sendEvent(
              ReflectionUtils.getMethod(ClusterRecordObserver.class, "onDeactivating", ClusterRecord.class),
              ClusterRecordServiceImpl.class, clusterRecord);
        } catch (Exception e) {
          log.error("Failed to inform the Observers for ClusterRecord with id={}", clusterRecord.getCluster(), e);
        }
      }
    }
    return clusterRecordDao.setStatus(accountId, cloudProviderId, true);
  }

  @Override
  public boolean delete(String accountId, String cloudProviderId) {
    // get the list of Clusters associated with the cloudProvider
    List<ClusterRecord> clusterRecords = list(accountId, cloudProviderId, false);
    if (isNull(clusterRecords)) {
      log.warn("Cloud Provider with id={} has no Clusters to be deleted.", cloudProviderId);
    } else {
      for (ClusterRecord clusterRecord : clusterRecords) {
        try {
          // Both subject and remote Observer are needed since in few places DMS might not be present
          subject.fireInform(ClusterRecordObserver::onDeleting, clusterRecord);
          remoteObserverInformer.sendEvent(
              ReflectionUtils.getMethod(ClusterRecordObserver.class, "onDeleting", ClusterRecord.class),
              ClusterRecordServiceImpl.class, clusterRecord);
        } catch (Exception e) {
          log.error("Failed to inform the Observers for ClusterRecord with id={}", clusterRecord.getCluster(), e);
        }
      }
    }
    return clusterRecordDao.delete(accountId, cloudProviderId);
  }

  @Override
  public ClusterRecord attachPerpetualTaskId(ClusterRecord clusterRecord, String taskId) {
    return clusterRecordDao.insertTask(clusterRecord, taskId);
  }

  @Override
  public ClusterRecord removePerpetualTaskId(ClusterRecord clusterRecord, String taskId) {
    return clusterRecordDao.removeTask(clusterRecord, taskId);
  }

  @Override
  public ClusterRecord from(SettingAttribute cloudProvider) {
    ClusterRecord clusterRecord = null;
    Cluster cluster = null;

    switch (cloudProvider.getValue().getType()) {
      case "KUBERNETES_CLUSTER":
        cluster = DirectKubernetesCluster.builder()
                      .cloudProviderId(cloudProvider.getUuid())
                      .clusterName(cloudProvider.getName())
                      .build();
        break;
      default:
        break;
    }
    if (!isNull(cluster)) {
      clusterRecord = ClusterRecord.builder().accountId(cloudProvider.getAccountId()).cluster(cluster).build();
    }
    return clusterRecord;
  }

  @Override
  public ClusterRecord from(InfrastructureDefinition infrastructureDefinition) {
    ClusterRecord clusterRecord = null;
    Cluster cluster = null;

    InfraMappingInfrastructureProvider infraMappingInfrastructureProvider =
        infrastructureDefinition.getInfrastructure();
    switch (infraMappingInfrastructureProvider.getInfrastructureType()) {
      case AWS_ECS:
        AwsEcsInfrastructure ecsInfrastructure = (AwsEcsInfrastructure) infraMappingInfrastructureProvider;
        if (isNotEmpty(ecsInfrastructure.getClusterName())) {
          cluster = EcsCluster.builder()
                        .cloudProviderId(ecsInfrastructure.getCloudProviderId())
                        .region(ecsInfrastructure.getRegion())
                        .clusterName(ecsInfrastructure.getClusterName())
                        .build();
        }
        break;
      case DIRECT_KUBERNETES:
        DirectKubernetesInfrastructure k8sInfrastructure =
            (DirectKubernetesInfrastructure) infraMappingInfrastructureProvider;
        String cloudProviderId = k8sInfrastructure.getCloudProviderId();
        String clusterName = k8sInfrastructure.getClusterName();
        if (StringUtils.isBlank(clusterName)) {
          clusterName = settingsService.get(cloudProviderId).getName();
        }
        cluster = DirectKubernetesCluster.builder().cloudProviderId(cloudProviderId).clusterName(clusterName).build();
        break;
      case GCP_KUBERNETES_ENGINE:
        GoogleKubernetesEngine gcpK8SInfrastructure = (GoogleKubernetesEngine) infraMappingInfrastructureProvider;
        cluster = GcpKubernetesCluster.builder()
                      .cloudProviderId(gcpK8SInfrastructure.getCloudProviderId())
                      .clusterName(gcpK8SInfrastructure.getClusterName())
                      .build();
        break;
      case AZURE_KUBERNETES:
        AzureKubernetesService azureKubernetesInfrastructure =
            (AzureKubernetesService) infraMappingInfrastructureProvider;
        cluster = AzureKubernetesCluster.builder()
                      .cloudProviderId(azureKubernetesInfrastructure.getCloudProviderId())
                      .subscriptionId(azureKubernetesInfrastructure.getSubscriptionId())
                      .resourceGroup(azureKubernetesInfrastructure.getResourceGroup())
                      .clusterName(azureKubernetesInfrastructure.getClusterName())
                      .build();
        break;
      default:
        break;
    }

    if (!isNull(cluster)) {
      Application application = appService.get(infrastructureDefinition.getAppId());
      clusterRecord = ClusterRecord.builder().accountId(application.getAccountId()).cluster(cluster).build();
    }
    return clusterRecord;
  }

  @Override
  public ClusterRecord from(InfrastructureMapping infraMapping) {
    ClusterRecord clusterRecord = null;
    Cluster cluster = null;

    switch (InfrastructureMappingType.valueOf(infraMapping.getInfraMappingType())) {
      case DIRECT_KUBERNETES:
        DirectKubernetesInfrastructureMapping k8sInfraMapping = (DirectKubernetesInfrastructureMapping) infraMapping;
        cluster = DirectKubernetesCluster.builder()
                      .cloudProviderId(k8sInfraMapping.getComputeProviderSettingId())
                      .clusterName(k8sInfraMapping.getComputeProviderName())
                      .build();
        if (StringUtils.isBlank(k8sInfraMapping.getComputeProviderName())) {
          log.warn("ClusterRecord derived from Infrastructure mapping {} is missing cluster name", k8sInfraMapping);
          log.warn("Stacktrace:\n", new Throwable());
        }
        break;
      case AWS_ECS:
        EcsInfrastructureMapping ecsInfraMapping = (EcsInfrastructureMapping) infraMapping;
        if (isNotEmpty(ecsInfraMapping.getClusterName())) {
          cluster = EcsCluster.builder()
                        .cloudProviderId(ecsInfraMapping.getComputeProviderSettingId())
                        .region(ecsInfraMapping.getRegion())
                        .clusterName(ecsInfraMapping.getClusterName())
                        .build();
        }
        break;
      case GCP_KUBERNETES:
        GcpKubernetesInfrastructureMapping gcpKubernetesInfrastructureMapping =
            (GcpKubernetesInfrastructureMapping) infraMapping;
        cluster = GcpKubernetesCluster.builder()
                      .cloudProviderId(gcpKubernetesInfrastructureMapping.getComputeProviderSettingId())
                      .clusterName(gcpKubernetesInfrastructureMapping.getClusterName())
                      .build();
        break;
      case AZURE_KUBERNETES:
        AzureKubernetesInfrastructureMapping azureKubernetesInfrastructureMapping =
            (AzureKubernetesInfrastructureMapping) infraMapping;
        cluster = AzureKubernetesCluster.builder()
                      .cloudProviderId(azureKubernetesInfrastructureMapping.getComputeProviderSettingId())
                      .subscriptionId(azureKubernetesInfrastructureMapping.getSubscriptionId())
                      .resourceGroup(azureKubernetesInfrastructureMapping.getResourceGroup())
                      .clusterName(azureKubernetesInfrastructureMapping.getClusterName())
                      .build();
        break;
      default:
        break;
    }

    if (!isNull(cluster)) {
      clusterRecord = ClusterRecord.builder().accountId(infraMapping.getAccountId()).cluster(cluster).build();
    }

    return clusterRecord;
  }

  @Override
  public List<ClusterRecord> listCeEnabledClusters(String accountId) {
    return clusterRecordDao.listCeEnabledClusters(accountId);
  }
}
