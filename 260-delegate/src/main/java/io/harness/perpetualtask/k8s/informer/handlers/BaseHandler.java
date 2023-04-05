/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.informer.handlers;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.CcmConstants;
import io.harness.event.client.EventPublisher;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.watch.K8sObjectReference;
import io.harness.perpetualtask.k8s.watch.K8sWatchEvent;
import io.harness.perpetualtask.k8s.watch.K8sWorkloadSpec;
import io.harness.reflection.ReflectionUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Timestamp;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.ModelMapper;
import io.kubernetes.client.util.Yaml;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joor.Reflect;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public abstract class BaseHandler<ApiType extends KubernetesObject> implements ResourceEventHandler<ApiType> {
  private static final String METADATA = "metadata";
  private static final String STATUS = "status";
  public static final Integer VERSION = 1;

  static {
    initModelMap();
  }

  private static void initModelMap() {
    // Workaround for classpath scanning issues with nested jars
    // See https://github.com/kubernetes-client/java/issues/365
    try {
      ModelMapper.addModelMap("", "v1", "Pod", "pods", true, V1Pod.class);
      ModelMapper.addModelMap("", "v1", "Node", "nodes", false, V1Node.class);
      ModelMapper.addModelMap("", "v1", "Event", "events", true, CoreV1Event.class);
      ModelMapper.addModelMap("apps", "v1", "Deployment", "deployments", true, V1Deployment.class);
      ModelMapper.addModelMap("apps", "v1", "ReplicaSet", "replicasets", true, V1ReplicaSet.class);
      ModelMapper.addModelMap("apps", "v1", "StatefulSet", "statefulsets", true, V1StatefulSet.class);
      ModelMapper.addModelMap("apps", "v1", "DaemonSet", "daemonsets", true, V1DaemonSet.class);
      ModelMapper.addModelMap("batch", "v1", "Job", "jobs", true, V1Job.class);
      ModelMapper.addModelMap("batch", "v1beta1", "CronJob", "cronjobs", true, V1CronJob.class);
    } catch (Exception e) {
      log.error("Unexpected exception while loading classes", e);
    }
  }

  private final EventPublisher eventPublisher;
  private final ClusterDetails clusterDetails;

  public BaseHandler(EventPublisher eventPublisher, ClusterDetails clusterDetails) {
    this.eventPublisher = eventPublisher;
    this.clusterDetails = clusterDetails;
  }

  private void publishWatchEvent(K8sWatchEvent resourceDetailsProto, Timestamp occurredAt) {
    eventPublisher.publishMessage(K8sWatchEvent.newBuilder(resourceDetailsProto)
                                      .setClusterId(clusterDetails.getClusterId())
                                      .setCloudProviderId(clusterDetails.getCloudProviderId())
                                      .setClusterName(clusterDetails.getClusterName())
                                      .setKubeSystemUid(clusterDetails.getKubeSystemUid())
                                      .build(),
        occurredAt, ImmutableMap.of(CcmConstants.CLUSTER_ID_IDENTIFIER, clusterDetails.getClusterId()));
  }

  final void publishWorkloadSpec(K8sWorkloadSpec workloadSpecProto, Timestamp occurredAt) {
    eventPublisher.publishMessage(K8sWorkloadSpec.newBuilder(workloadSpecProto)
                                      .setClusterId(clusterDetails.getClusterId())
                                      .setCloudProviderId(clusterDetails.getCloudProviderId())
                                      .setClusterName(clusterDetails.getClusterName())
                                      .setKubeSystemUid(clusterDetails.getKubeSystemUid())
                                      .build(),
        occurredAt, ImmutableMap.of(CcmConstants.CLUSTER_ID_IDENTIFIER, clusterDetails.getClusterId()));
  }

  @Override
  public void onAdd(ApiType resource) {
    handleMissingKindAndApiVersion(resource);
    log.debug("Added resource: {}", ResourceDetails.ofResource(resource));
    V1OwnerReference controller = getController(resource);
    if (controller != null) {
      log.debug("Skipping publish for resource added as it has controller: {}", controller);
    } else {
      V1ObjectMeta objectMeta = getMetadata(resource);
      Instant creationTimestamp = Optional.ofNullable(objectMeta)
                                      .map(V1ObjectMeta::getCreationTimestamp)
                                      .map(dt -> Instant.ofEpochMilli(dt.toInstant().toEpochMilli()))
                                      .orElse(Instant.EPOCH);
      Timestamp occurredAt = HTimestamps.fromInstant(creationTimestamp);
      publishWatchEvent(K8sWatchEvent.newBuilder()
                            .setType(K8sWatchEvent.Type.TYPE_ADDED)
                            .setResourceRef(createObjectReference(resource))
                            .setNewResourceVersion(getResourceVersion(resource))
                            .setNewResourceYaml(yamlDump(resource))
                            .setDescription(String.format("%s created", getKind()))
                            .build(),
          occurredAt);
      publishWorkloadSpecOnAdd(resource, occurredAt);
    }
  }

  protected void publishWorkloadSpecOnAdd(ApiType resource, Timestamp occurredAt) {
    // default noop
  }

  protected void publishWorkloadSpecIfChangedOnUpdate(ApiType oldResource, ApiType newResource, Timestamp occurredAt) {
    // default noop
  }

  private void handleMissingKindAndApiVersion(ApiType resource) {
    if (Reflect.on(resource).get("kind") == null) {
      Reflect.on(resource).set("kind", getKind());
    }
    if (Reflect.on(resource).get("apiVersion") == null) {
      Reflect.on(resource).set("apiVersion", getApiVersion());
    }
  }

  @Override
  public void onUpdate(ApiType oldResource, ApiType newResource) {
    // Publish change event only if the spec changes, and the resource does not have a controlling owner (in which case
    // the change will be captured in owner)
    V1OwnerReference controller = getController(oldResource);
    if (controller != null) {
      log.debug("Skipping publish for resource updated as it has controller: {}", controller);
      return;
    }
    handleMissingKindAndApiVersion(oldResource);
    handleMissingKindAndApiVersion(newResource);
    ResourceDetails oldResourceDetails = ResourceDetails.ofResource(oldResource);
    ResourceDetails newResourceDetails = ResourceDetails.ofResource(newResource);
    log.debug("Resource: {} updated from {} to {}", oldResourceDetails, oldResourceDetails.getResourceVersion(),
        newResourceDetails.getResourceVersion());
    String oldYaml = yamlDump(oldResource);
    String newYaml = yamlDump(newResource);
    boolean specChanged = !StringUtils.equals(oldYaml, newYaml);
    K8sObjectReference objectReference = createObjectReference(oldResource);
    log.debug("Updated Resource: {}, SpecChanged: {}", objectReference, specChanged);
    if (!specChanged) {
      log.debug("Skipping publish for resource updated since no yaml change");
    } else {
      Timestamp occurredAt = HTimestamps.fromInstant(Instant.now());
      K8sWatchEvent k8sWatchEvent = K8sWatchEvent.newBuilder()
                                        .setType(K8sWatchEvent.Type.TYPE_UPDATED)
                                        .setResourceRef(objectReference)
                                        .setOldResourceVersion(getResourceVersion(oldResource))
                                        .setOldResourceYaml(oldYaml)
                                        .setNewResourceVersion(getResourceVersion(newResource))
                                        .setNewResourceYaml(newYaml)
                                        .setDescription(String.format("%s updated", getKind()))
                                        .build();
      publishWatchEvent(k8sWatchEvent, occurredAt);
      publishWorkloadSpecIfChangedOnUpdate(oldResource, newResource, occurredAt);
    }
  }

  private String yamlDump(ApiType resource) {
    Reflect resourceReflection = Reflect.on(resource);
    // Save status and metadata values.
    Object savedStatus = resourceReflection.get(STATUS);
    V1ObjectMeta savedMetadata = getMetadata(resource);
    try {
      resourceReflection.set(STATUS, null);
      resourceReflection.set(METADATA, null);

      V1ObjectMetaBuilder builder = new V1ObjectMetaBuilder();
      if (savedMetadata != null) {
        builder.withName(savedMetadata.getName())
            .withNamespace(savedMetadata.getNamespace())
            .withLabels(savedMetadata.getLabels())
            .withAnnotations(savedMetadata.getAnnotations())
            .withUid(savedMetadata.getUid());
      }
      resourceReflection.set(METADATA, builder.build());

      return Yaml.dump(resource);
    } finally {
      // Restore status and metadata.
      resourceReflection.set(STATUS, savedStatus);
      resourceReflection.set(METADATA, savedMetadata);
    }
  }

  @Override
  public void onDelete(ApiType resource, boolean finalStateUnknown) {
    handleMissingKindAndApiVersion(resource);
    log.debug("Delete resource: {}, finalStateUnknown: {}", ResourceDetails.ofResource(resource), finalStateUnknown);
    V1OwnerReference controller = getController(resource);
    if (controller != null) {
      log.debug("Skipping publish for resource deleted as it has controller: {}", controller);
    } else {
      K8sWatchEvent.Builder builder = K8sWatchEvent.newBuilder()
                                          .setType(K8sWatchEvent.Type.TYPE_DELETED)
                                          .setResourceRef(createObjectReference(resource))
                                          .setDescription(String.format("%s deleted", getKind()));
      if (!finalStateUnknown) {
        builder.setOldResourceVersion(getResourceVersion(resource)).setOldResourceYaml(yamlDump(resource));
      } else {
        // Unobserved deletion - we didn't watch deletion but noticed it during subsequent re-list.
        // TODO(avmohan): Remove this log. (Temporarily added to check how frequent)
        log.warn("Deletion with finalStateUnknown");
      }
      publishWatchEvent(builder.build(), HTimestamps.fromInstant(Instant.now()));
    }
  }

  private K8sObjectReference createObjectReference(ApiType resource) {
    K8sObjectReference.Builder builder = K8sObjectReference.newBuilder();
    V1ObjectMeta objectMeta = getMetadata(resource);
    if (objectMeta != null) {
      if (objectMeta.getNamespace() != null) {
        builder.setNamespace(objectMeta.getNamespace());
      }
      if (objectMeta.getName() != null) {
        builder.setName(objectMeta.getName());
      }
      if (objectMeta.getUid() != null) {
        builder.setUid(objectMeta.getUid());
      }
    }
    builder.setKind(getKind());
    return builder.build();
  }

  abstract String getKind();
  abstract String getApiVersion();

  private V1ObjectMeta getMetadata(ApiType resource) {
    return (V1ObjectMeta) ReflectionUtils.getFieldValue(resource, METADATA);
  }

  private String getResourceVersion(ApiType resource) {
    return Optional.ofNullable(getMetadata(resource)).map(V1ObjectMeta::getResourceVersion).orElse("");
  }

  private V1OwnerReference getController(ApiType resource) {
    V1ObjectMeta metadata = getMetadata(resource);
    if (metadata != null) {
      List<V1OwnerReference> ownerReferences = metadata.getOwnerReferences();
      if (ownerReferences != null) {
        for (V1OwnerReference ownerReference : ownerReferences) {
          if (Boolean.TRUE.equals(ownerReference.getController())) {
            return ownerReference;
          }
        }
      }
    }
    return null;
  }

  @Value
  @Builder
  private static class ResourceDetails {
    String kind;
    String namespace;
    String name;
    String uid;
    String resourceVersion;

    static ResourceDetails ofResource(Object resource) {
      Map<String, Object> fieldValues = ReflectionUtils.getFieldValues(resource, ImmutableSet.of("kind", METADATA));
      String kind = (String) fieldValues.get("kind");
      V1ObjectMeta objectMeta = (V1ObjectMeta) fieldValues.computeIfAbsent(METADATA, k -> new V1ObjectMeta());
      return ResourceDetails.builder()
          .kind(kind)
          .name(objectMeta.getName())
          .namespace(objectMeta.getNamespace())
          .uid(objectMeta.getUid())
          .resourceVersion(objectMeta.getResourceVersion())
          .build();
    }
  }
}
