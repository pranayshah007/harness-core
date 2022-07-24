/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.ccm.commons.constants.Constants.CLUSTER_ID_IDENTIFIER;
import static io.harness.ccm.commons.constants.Constants.UID;
import static io.harness.perpetualtask.k8s.watch.PVEvent.EventType.EVENT_TYPE_EXPANSION;
import static io.harness.perpetualtask.k8s.watch.PVEvent.EventType.EVENT_TYPE_STOP;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Optional.ofNullable;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.client.EventPublisher;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.Timestamp;
import io.kubernetes.client.informer.EventType;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.StorageV1Api;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.util.CallGeneratorParams;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class PVWatcher implements ResourceEventHandler<V1PersistentVolume> {
  private final String clusterId;
  private final boolean isClusterSeen;
  private final Set<String> publishedPVs;
  private final EventPublisher eventPublisher;
  private final StorageV1Api storageV1Api;

  private final PVInfo pvInfoPrototype;
  private final PVEvent pvEventPrototype;

  private static final String EVENT_LOG_MSG = "V1PersistentVolume: {}, action: {}";
  private static final String ERROR_PUBLISH_LOG_MSG = "Error publishing V1PersistentVolume.{} event.";

  private final LoadingCache<String, Map<String, String>> storageClassParamsCache;

  @Inject
  public PVWatcher(@Assisted ApiClient apiClient, @Assisted ClusterDetails params,
      @Assisted SharedInformerFactory sharedInformerFactory, EventPublisher eventPublisher) {
    log.info(
        "Creating new PVWatcher for cluster with id: {} name: {} ", params.getClusterId(), params.getClusterName());

    this.clusterId = params.getClusterId();
    this.isClusterSeen = params.isSeen();
    this.publishedPVs = new ConcurrentSkipListSet<>();
    this.eventPublisher = eventPublisher;

    this.pvInfoPrototype = PVInfo.newBuilder()
                               .setCloudProviderId(params.getCloudProviderId())
                               .setClusterId(clusterId)
                               .setClusterName(params.getClusterName())
                               .setKubeSystemUid(params.getKubeSystemUid())
                               .build();

    this.pvEventPrototype = PVEvent.newBuilder()
                                .setCloudProviderId(params.getCloudProviderId())
                                .setClusterId(clusterId)
                                .setKubeSystemUid(params.getKubeSystemUid())
                                .build();

    this.storageV1Api = new StorageV1Api(apiClient);
    this.storageClassParamsCache =
        Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build(key -> this.storageV1Api.readStorageClass(key, null, null, null).getParameters());

    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    sharedInformerFactory
        .sharedIndexInformerFor((CallGeneratorParams callGeneratorParams)
                                    -> coreV1Api.listPersistentVolumeCall(null, null, null, null, null, null,
                                        callGeneratorParams.resourceVersion, null, callGeneratorParams.timeoutSeconds,
                                        callGeneratorParams.watch, null),
            V1PersistentVolume.class, V1PersistentVolumeList.class)
        .addEventHandler(this);
  }

  @Override
  public void onAdd(V1PersistentVolume persistentVolume) {
    try {
      log.debug(EVENT_LOG_MSG, persistentVolume.getMetadata().getUid(), EventType.ADDED);

      OffsetDateTime creationTimestamp = persistentVolume.getMetadata().getCreationTimestamp();
      if (!isClusterSeen || creationTimestamp == null
          || creationTimestamp.isAfter(OffsetDateTime.now().minusHours(2))) {
        publishPVInfo(persistentVolume);
      } else {
        publishedPVs.add(persistentVolume.getMetadata().getUid());
      }

      publishedPVs.add(persistentVolume.getMetadata().getUid());
    } catch (Exception ex) {
      log.error(ERROR_PUBLISH_LOG_MSG, EventType.ADDED, ex);
    }
  }

  @Override
  public void onUpdate(V1PersistentVolume oldPersistentVolume, V1PersistentVolume persistentVolume) {
    try {
      log.debug(EVENT_LOG_MSG, persistentVolume.getMetadata().getUid(), EventType.MODIFIED);

      if (!publishedPVs.contains(persistentVolume.getMetadata().getUid())) {
        publishPVInfo(persistentVolume);
      }

      long oldVolSize = K8sResourceUtils.getStorageCapacity(oldPersistentVolume.getSpec()).getAmount();
      long newVolSize = K8sResourceUtils.getStorageCapacity(persistentVolume.getSpec()).getAmount();

      if (oldVolSize != newVolSize) {
        log.debug("Volume change observed from {} to {}", oldVolSize, newVolSize);
        publishPVEvent(persistentVolume, HTimestamps.fromMillis(OffsetDateTime.now().toInstant().toEpochMilli()),
            EVENT_TYPE_EXPANSION);
      }
    } catch (Exception ex) {
      log.error(ERROR_PUBLISH_LOG_MSG, EventType.MODIFIED, ex);
    }
  }

  @Override
  public void onDelete(V1PersistentVolume persistentVolume, boolean deletedFinalStateUnknown) {
    try {
      log.debug(EVENT_LOG_MSG, persistentVolume.getMetadata().getUid(), EventType.DELETED);

      publishPVEvent(persistentVolume,
          HTimestamps.fromMillis(ofNullable(persistentVolume.getMetadata().getDeletionTimestamp())
                                     .orElse(OffsetDateTime.now())
                                     .toInstant()
                                     .toEpochMilli()),
          EVENT_TYPE_STOP);

      publishedPVs.remove(persistentVolume.getMetadata().getUid());
    } catch (Exception ex) {
      log.error(ERROR_PUBLISH_LOG_MSG, EventType.DELETED, ex);
    }
  }

  private void publishPVInfo(V1PersistentVolume persistentVolume) {
    Timestamp timestamp =
        HTimestamps.fromMillis(persistentVolume.getMetadata().getCreationTimestamp().toInstant().toEpochMilli());

    PVInfo pvInfo =
        PVInfo.newBuilder(pvInfoPrototype)
            .setPvType(getPvType(persistentVolume.getSpec()))
            .setPvUid(persistentVolume.getMetadata().getUid())
            .setPvName(persistentVolume.getMetadata().getName())
            .setCreationTimestamp(timestamp)
            .putAllLabels(firstNonNull(persistentVolume.getMetadata().getLabels(), Collections.emptyMap()))
            .putAllLabels(firstNonNull(persistentVolume.getMetadata().getAnnotations(), Collections.emptyMap()))
            .setClaimName(getClaimName(persistentVolume.getSpec()))
            .setClaimNamespace(getClaimNamespace(persistentVolume.getSpec()))
            .putAllStorageClassParams(firstNonNull(getStorageClassParameters(persistentVolume), Collections.emptyMap()))
            .setCapacity(K8sResourceUtils.getStorageCapacity(persistentVolume.getSpec()))
            .build();
    eventPublisher.publishMessage(pvInfo, timestamp,
        ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId, UID, persistentVolume.getMetadata().getUid()));
  }

  private String getClaimNamespace(V1PersistentVolumeSpec spec) {
    if (spec != null && spec.getClaimRef() != null && spec.getClaimRef().getNamespace() != null) {
      return spec.getClaimRef().getNamespace();
    }
    return "";
  }

  private String getClaimName(V1PersistentVolumeSpec spec) {
    if (spec != null && spec.getClaimRef() != null && spec.getClaimRef().getName() != null) {
      return spec.getClaimRef().getName();
    }
    return "";
  }

  public void publishPVEvent(V1PersistentVolume persistentVolume, Timestamp timestamp, PVEvent.EventType type) {
    final String uid = persistentVolume.getMetadata().getUid();

    PVEvent pvEvent = PVEvent.newBuilder(pvEventPrototype)
                          .setPvUid(uid)
                          .setPvName(persistentVolume.getMetadata().getName())
                          .setEventType(type)
                          .setTimestamp(timestamp)
                          .build();

    log.debug("Publishing : {}", pvEvent.getEventType());
    eventPublisher.publishMessage(pvEvent, timestamp, ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId, UID, uid));
  }

  public PVInfo.PVType getPvType(V1PersistentVolumeSpec spec) {
    if (spec.getGcePersistentDisk() != null) {
      return PVInfo.PVType.PV_TYPE_GCE_PERSISTENT_DISK;
    } else if (spec.getAwsElasticBlockStore() != null) {
      return PVInfo.PVType.PV_TYPE_AWS_EBS;
    } else if (spec.getAzureDisk() != null) {
      return PVInfo.PVType.PV_TYPE_AZURE_DISK;
    } else if (spec.getNfs() != null) {
      return PVInfo.PVType.PV_TYPE_NFS;
    }
    return PVInfo.PVType.PV_TYPE_UNSPECIFIED;
  }

  public Map<String, String> getStorageClassParameters(V1PersistentVolume persistentVolume) {
    if (persistentVolume.getSpec() != null && persistentVolume.getSpec().getStorageClassName() != null) {
      try {
        return this.storageClassParamsCache.get(persistentVolume.getSpec().getStorageClassName());
      } catch (Exception ex) {
        log.warn("Failed to get storageClassName {}", persistentVolume.getSpec().getStorageClassName(), ex);
      }
    }
    return null;
  }
}
