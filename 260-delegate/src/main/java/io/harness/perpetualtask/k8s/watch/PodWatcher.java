/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.ccm.commons.constants.Constants.CLUSTER_ID_IDENTIFIER;
import static io.harness.ccm.commons.constants.Constants.UID;
import static io.harness.perpetualtask.k8s.utils.DebugConstants.RELATIVITY_CLUSTER_IDS;
import static io.harness.perpetualtask.k8s.watch.PodEvent.EventType.EVENT_TYPE_TERMINATED;
import static io.harness.perpetualtask.k8s.watch.Volume.VolumeType.VOLUME_TYPE_PVC;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Optional.ofNullable;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.client.EventPublisher;
import io.harness.event.payloads.CeExceptionMessage;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.utils.K8sWatcherHelper;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.TypeRegistry;
import io.kubernetes.client.informer.EventType;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.util.CallGeneratorParams;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class PodWatcher implements ResourceEventHandler<V1Pod> {
  private static final TypeRegistry TYPE_REGISTRY =
      TypeRegistry.newBuilder().add(PodInfo.getDescriptor()).add(PodEvent.getDescriptor()).build();

  private final String clusterId;
  private final EventPublisher eventPublisher;
  private final PVCFetcher pvcFetcher;
  private final NamespaceFetcher namespaceFetcher;
  private final Set<String> publishedPods;

  private final PodInfo podInfoPrototype;
  private final PodEvent podEventPrototype;
  private final boolean isClusterSeen;

  private final K8sControllerFetcher controllerFetcher;

  private static final String POD_EVENT_MSG = "Pod: {}, action: {}";
  private static final String FAILED_PUBLISH_MSG = "Error publishing V1Pod.{} event.";
  private static final String MESSAGE_PROCESSOR_TYPE_EXCEPTION = "EXCEPTION";

  @Inject
  public PodWatcher(@Assisted ApiClient apiClient, @Assisted ClusterDetails params,
      @Assisted K8sControllerFetcher controllerFetcher, @Assisted SharedInformerFactory sharedInformerFactory,
      @Assisted PVCFetcher pvcFetcher, @Assisted NamespaceFetcher namespaceFetcher, EventPublisher eventPublisher) {
    this.controllerFetcher = controllerFetcher;
    this.pvcFetcher = pvcFetcher;
    this.namespaceFetcher = namespaceFetcher;
    log.info(
        "Creating new PodWatcher for cluster with id: {} name: {} ", params.getClusterId(), params.getClusterName());
    this.clusterId = params.getClusterId();
    this.isClusterSeen = params.isSeen();
    this.publishedPods = new ConcurrentSkipListSet<>();
    this.eventPublisher = eventPublisher;

    podInfoPrototype = PodInfo.newBuilder()
                           .setCloudProviderId(params.getCloudProviderId())
                           .setClusterId(clusterId)
                           .setClusterName(params.getClusterName())
                           .setKubeSystemUid(params.getKubeSystemUid())
                           .build();
    podEventPrototype = PodEvent.newBuilder()
                            .setCloudProviderId(params.getCloudProviderId())
                            .setClusterId(clusterId)
                            .setKubeSystemUid(params.getKubeSystemUid())
                            .build();

    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    sharedInformerFactory
        .sharedIndexInformerFor(
            (CallGeneratorParams callGeneratorParams)
                -> {
              log.info("Pod watcher :: Resource version: {}, timeoutSeconds: {}, watch: {}",
                  callGeneratorParams.resourceVersion, callGeneratorParams.timeoutSeconds, callGeneratorParams.watch);
              if (!"0".equals(callGeneratorParams.resourceVersion)) {
                K8sWatcherHelper.updateLastSeen(
                    String.format(K8sWatcherHelper.POD_WATCHER_PREFIX, clusterId), Instant.now());
              }
              try {
                return coreV1Api.listPodForAllNamespacesCall(null, null, null, null, null, null,
                    callGeneratorParams.resourceVersion, null, callGeneratorParams.timeoutSeconds,
                    callGeneratorParams.watch, null);
              } catch (Exception e) {
                log.error("Unknown exception occurred for listPodForAllNamespacesCall", e);
                throw e;
              }
            },
            V1Pod.class, V1PodList.class)
        .addEventHandler(this);
  }

  @Override
  public void onAdd(V1Pod pod) {
    try {
      log.debug(POD_EVENT_MSG, pod.getMetadata().getUid(), EventType.ADDED);
      DateTime creationTimestamp = pod.getMetadata().getCreationTimestamp();
      if (!isClusterSeen || creationTimestamp == null || creationTimestamp.isAfter(DateTime.now().minusHours(2))) {
        eventReceived(pod);
      } else {
        publishedPods.add(pod.getMetadata().getUid());
      }
    } catch (Exception ex) {
      log.error(FAILED_PUBLISH_MSG, EventType.ADDED, ex);
    }
  }

  @Override
  public void onUpdate(V1Pod oldPod, V1Pod pod) {
    try {
      log.debug(POD_EVENT_MSG, pod.getMetadata().getUid(), EventType.MODIFIED);

      eventReceived(pod);
    } catch (Exception ex) {
      log.error(FAILED_PUBLISH_MSG, EventType.MODIFIED, ex);
    }
  }

  @Override
  public void onDelete(V1Pod pod, boolean deletedFinalStateUnknown) {
    try {
      log.debug(POD_EVENT_MSG, pod.getMetadata().getUid(), EventType.DELETED);

      eventReceived(pod);
    } catch (Exception ex) {
      log.error(FAILED_PUBLISH_MSG, EventType.DELETED, ex);
    }
  }

  public void eventReceived(V1Pod pod) {
    String uid = pod.getMetadata().getUid();
    V1PodCondition podScheduledCondition = getPodScheduledCondition(pod);

    if (podScheduledCondition != null && !publishedPods.contains(uid)) {
      Timestamp creationTimestamp = HTimestamps.fromMillis(pod.getMetadata().getCreationTimestamp().getMillis());

      PodInfo podInfo =
          PodInfo.newBuilder(podInfoPrototype)
              .setPodUid(uid)
              .setPodName(pod.getMetadata().getName())
              .setNamespace(pod.getMetadata().getNamespace())
              .setNodeName(pod.getSpec().getNodeName())
              .setTotalResource(K8sResourceUtils.getEffectiveResources(pod.getSpec()))
              .addAllVolume(getAllVolumes(pod))
              .setQosClass(pod.getStatus().getQosClass())
              .setCreationTimestamp(creationTimestamp)
              .addAllContainers(getAllContainers(pod.getSpec().getContainers()))
              .putAllLabels(firstNonNull(pod.getMetadata().getLabels(), Collections.emptyMap()))
              .putAllMetadataAnnotations(firstNonNull(pod.getMetadata().getAnnotations(), Collections.emptyMap()))
              .putAllNamespaceLabels(
                  firstNonNull(getNamespaceLabels(pod.getMetadata().getNamespace()), Collections.emptyMap()))
              .setTopLevelOwner(controllerFetcher.getTopLevelOwner(pod))
              .build();
      logMessage(podInfo);

      eventPublisher.publishMessage(
          podInfo, creationTimestamp, ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId, UID, uid));
      if (RELATIVITY_CLUSTER_IDS.contains(clusterId)) {
        log.info("published PodInfo UID:[{}], Name:[{}]", uid, pod.getMetadata().getName());
      }

      publishedPods.add(uid);
    } else if (podScheduledCondition == null) {
      if (RELATIVITY_CLUSTER_IDS.contains(clusterId)) {
        log.warn("podScheduledCondition is null Pod UID:[{}], Name:[{}]", uid, pod.getMetadata().getName());
      }
    }

    if (isPodDeleted(pod)) {
      Timestamp timestamp = HTimestamps.fromMillis(pod.getMetadata().getDeletionTimestamp().getMillis());
      PodEvent podEvent = PodEvent.newBuilder(podEventPrototype)
                              .setPodUid(uid)
                              .setType(EVENT_TYPE_TERMINATED)
                              .setTimestamp(timestamp)
                              .build();
      logMessage(podEvent);
      eventPublisher.publishMessage(podEvent, timestamp, ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId, UID, uid));
      if (RELATIVITY_CLUSTER_IDS.contains(clusterId)) {
        log.info("published PodEvent UID:[{}], Name:[{}]", uid, pod.getMetadata().getName());
      }
      publishedPods.remove(uid);
    }
  }

  private Map<String, String> getNamespaceLabels(String namespaceName) {
    try {
      return namespaceFetcher.getNamespaceByKey(namespaceName).getMetadata().getLabels();
    } catch (Exception ex) {
      log.warn("Failed to fetch namespaceLabels returning default", ex);
    }
    return null;
  }

  private List<io.harness.perpetualtask.k8s.watch.Volume> getAllVolumes(V1Pod pod) {
    String namespace = ofNullable(pod.getMetadata().getNamespace()).orElse("");
    List<io.harness.perpetualtask.k8s.watch.Volume> volumesList = new ArrayList<>();

    if (pod.getSpec().getVolumes() != null) {
      for (V1Volume volume : pod.getSpec().getVolumes()) {
        try {
          if (volume.getPersistentVolumeClaim() != null) {
            String claimName = volume.getPersistentVolumeClaim().getClaimName();
            try {
              V1PersistentVolumeClaim fetchedPvc = pvcFetcher.getPvcByKey(namespace, claimName);

              volumesList.add(io.harness.perpetualtask.k8s.watch.Volume.newBuilder()
                                  .setId(claimName)
                                  .setType(VOLUME_TYPE_PVC)
                                  .setRequest(K8sResourceUtils.getStorageRequest(fetchedPvc.getSpec().getResources()))
                                  .build());
            } catch (ApiException ex) {
              publishError(CeExceptionMessage.newBuilder()
                               .setClusterId(clusterId)
                               .setMessage(String.format("code=[%s] message=[%s] body=[%s]", ex.getCode(),
                                   ex.getMessage(), ex.getResponseBody()))
                               .build());
            }
          }
          // add other volumes here e.g., getAzureDisk, getAwsElasticBlockStore, getGcePersistentDisk
        } catch (Exception ex) {
          log.error("Error parsing Volume: {}", volume, ex);
        }
      }
    }
    return volumesList;
  }

  private void publishError(CeExceptionMessage ceExceptionMessage) {
    try {
      eventPublisher.publishMessage(ceExceptionMessage, HTimestamps.fromInstant(Instant.now()),
          ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId), MESSAGE_PROCESSOR_TYPE_EXCEPTION);
    } catch (Exception ex) {
      log.error("Failed to publish failure from PodWatcher to the Event Server.", ex);
    }
  }

  private boolean isPodDeleted(V1Pod pod) {
    return pod.getMetadata().getDeletionTimestamp() != null && pod.getMetadata().getDeletionGracePeriodSeconds() == 0L;
  }

  private boolean isPodInTerminalPhase(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    return status != null && ("Succeeded".equals(status.getPhase()) || "Failed".equals(status.getPhase()));
  }

  private List<Container> getAllContainers(List<V1Container> k8sContainerList) {
    List<Container> containerList = new ArrayList<>();
    for (V1Container k8sContainer : k8sContainerList) {
      Container container = Container.newBuilder()
                                .setName(k8sContainer.getName())
                                .setImage(k8sContainer.getImage())
                                .setResource(K8sResourceUtils.getResource(k8sContainer))
                                .build();
      containerList.add(container);
    }
    return containerList;
  }

  /**
   * Get the pod condition with type PodScheduled=true.
   * A pod occupies resource when type=PodScheduled and status=True.
   */
  private V1PodCondition getPodScheduledCondition(V1Pod pod) {
    V1PodStatus podStatus = pod.getStatus();
    if (podStatus != null && podStatus.getConditions() != null) {
      return podStatus.getConditions()
          .stream()
          .filter(c -> "PodScheduled".equals(c.getType()) && "True".equals(c.getStatus()))
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  private static void logMessage(Message message) {
    if (log.isDebugEnabled()) {
      try {
        log.info(JsonFormat.printer().usingTypeRegistry(TYPE_REGISTRY).print(message));
      } catch (InvalidProtocolBufferException e) {
        log.error(e.getMessage());
      }
    }
  }
}
