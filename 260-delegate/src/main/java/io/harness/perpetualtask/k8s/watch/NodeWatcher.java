/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import static io.harness.ccm.commons.constants.Constants.CLUSTER_ID_IDENTIFIER;
import static io.harness.ccm.commons.constants.Constants.UID;
import static io.harness.perpetualtask.k8s.watch.NodeEvent.EventType.EVENT_TYPE_STOP;

import static java.util.Optional.ofNullable;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.client.EventPublisher;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.Timestamp;
import io.kubernetes.client.informer.EventType;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.util.CallGeneratorParams;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class NodeWatcher implements ResourceEventHandler<V1Node> {
  private final EventPublisher eventPublisher;
  private final Set<String> publishedNodes;

  private final String clusterId;
  private final boolean isClusterSeen;
  private final NodeInfo nodeInfoPrototype;
  private final NodeEvent nodeEventPrototype;

  private static final String NODE_EVENT_MSG = "Node: {}, action: {}";
  private static final String ERROR_PUBLISH_MSG = "Error publishing V1Node.{} event.";

  @Inject
  public NodeWatcher(@Assisted ApiClient apiClient, @Assisted ClusterDetails params,
      @Assisted SharedInformerFactory sharedInformerFactory, EventPublisher eventPublisher) {
    log.info(
        "Creating new NodeWatcher for cluster with id: {} name: {} ", params.getClusterId(), params.getClusterName());

    this.clusterId = params.getClusterId();
    this.isClusterSeen = params.isSeen();
    this.eventPublisher = eventPublisher;
    this.publishedNodes = new ConcurrentSkipListSet<>();
    this.nodeInfoPrototype = NodeInfo.newBuilder()
                                 .setCloudProviderId(params.getCloudProviderId())
                                 .setClusterId(clusterId)
                                 .setClusterName(params.getClusterName())
                                 .setKubeSystemUid(params.getKubeSystemUid())
                                 .build();
    this.nodeEventPrototype = NodeEvent.newBuilder()
                                  .setCloudProviderId(params.getCloudProviderId())
                                  .setClusterId(clusterId)
                                  .setKubeSystemUid(params.getKubeSystemUid())
                                  .build();

    CoreV1Api coreV1Api = new CoreV1Api(apiClient);
    sharedInformerFactory
        .sharedIndexInformerFor(
            (CallGeneratorParams callGeneratorParams)
                -> {
              try {
                return coreV1Api.listNodeCall(null, null, null, null, null, null, callGeneratorParams.resourceVersion,
                    null, callGeneratorParams.timeoutSeconds, callGeneratorParams.watch, null);
              } catch (ApiException e) {
                log.error("Unknown exception occurred", e);
                throw e;
              }
            },
            V1Node.class, V1NodeList.class)
        .addEventHandler(this);
  }

  @Override
  public void onUpdate(V1Node oldNode, V1Node newNode) {
    try {
      log.debug(NODE_EVENT_MSG, newNode.getMetadata().getUid(), EventType.MODIFIED);

      publishNodeInfo(newNode);
    } catch (Exception ex) {
      log.error(ERROR_PUBLISH_MSG, EventType.MODIFIED, ex);
    }
  }

  @Override
  public void onAdd(V1Node node) {
    try {
      log.debug(NODE_EVENT_MSG, node.getMetadata().getUid(), EventType.ADDED);

      OffsetDateTime creationTimestamp = node.getMetadata().getCreationTimestamp();
      if (!isClusterSeen || creationTimestamp == null
          || creationTimestamp.isAfter(OffsetDateTime.now().minusHours(2))) {
        publishNodeInfo(node);
      } else {
        publishedNodes.add(node.getMetadata().getUid());
      }
    } catch (Exception ex) {
      log.error(ERROR_PUBLISH_MSG, EventType.ADDED, ex);
    }
  }

  @Override
  public void onDelete(V1Node node, boolean deletedFinalStateUnknown) {
    try {
      log.debug(NODE_EVENT_MSG, node.getMetadata().getUid(), EventType.DELETED);

      publishNodeStoppedEvent(node);
    } catch (Exception ex) {
      log.error(ERROR_PUBLISH_MSG, EventType.DELETED.name(), ex);
    }
  }

  public void publishNodeStoppedEvent(V1Node node) {
    final Timestamp timestamp = HTimestamps.fromMillis(
        ofNullable(node.getMetadata().getDeletionTimestamp()).orElse(OffsetDateTime.now()).toInstant().toEpochMilli());

    NodeEvent nodeStoppedEvent = NodeEvent.newBuilder(nodeEventPrototype)
                                     .setNodeUid(node.getMetadata().getUid())
                                     .setNodeName(node.getMetadata().getName())
                                     .setType(EVENT_TYPE_STOP)
                                     .setTimestamp(timestamp)
                                     .build();

    log.debug("Publishing event: {}", nodeStoppedEvent);
    eventPublisher.publishMessage(nodeStoppedEvent, timestamp,
        ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId, UID, node.getMetadata().getUid()));
    publishedNodes.remove(node.getMetadata().getUid());
  }

  public void publishNodeInfo(V1Node node) {
    if (!publishedNodes.contains(node.getMetadata().getUid())) {
      final Timestamp timestamp =
          HTimestamps.fromMillis(node.getMetadata().getCreationTimestamp().toInstant().toEpochMilli());

      NodeInfo nodeInfo =
          NodeInfo.newBuilder(nodeInfoPrototype)
              .setNodeUid(node.getMetadata().getUid())
              .setNodeName(node.getMetadata().getName())
              .setCreationTime(timestamp)
              .setProviderId(ofNullable(node.getSpec().getProviderID()).orElse(""))
              .putAllLabels(node.getMetadata().getLabels())
              .putAllAllocatableResource(K8sResourceUtils.getResourceMap(node.getStatus().getAllocatable()))
              .build();
      eventPublisher.publishMessage(
          nodeInfo, timestamp, ImmutableMap.of(CLUSTER_ID_IDENTIFIER, clusterId, UID, node.getMetadata().getUid()));

      log.info("Published Node Uid: {}, Name:{}", nodeInfo.getNodeUid(), nodeInfo.getNodeName());

      publishedNodes.add(node.getMetadata().getUid());
    } else {
      log.debug("NodeInfo for uid:{} already published", node.getMetadata().getUid());
    }
  }
}
