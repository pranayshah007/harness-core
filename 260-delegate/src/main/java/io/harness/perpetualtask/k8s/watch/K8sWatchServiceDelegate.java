/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.watch;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.k8s.apiclient.ApiClientFactory;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.perpetualtask.k8s.informer.ClusterDetails;
import io.harness.perpetualtask.k8s.informer.SharedInformerFactoryFactory;
import io.harness.perpetualtask.k8s.metrics.client.impl.DefaultK8sMetricsClient;
import io.harness.perpetualtask.k8s.utils.K8sWatcherHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Store;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class K8sWatchServiceDelegate {
  private static final Map<String, Class<? extends KubernetesObject>> KNOWN_WORKLOAD_TYPES =
      ImmutableMap.<String, Class<? extends KubernetesObject>>builder()
          .put("Deployment", V1Deployment.class)
          .put("ReplicaSet", V1ReplicaSet.class)
          .put("DaemonSet", V1DaemonSet.class)
          .put("StatefulSet", V1StatefulSet.class)
          .put("Job", V1Job.class)
          .put("CronJob", V1CronJob.class)
          .build();

  private final WatcherFactory watcherFactory;
  private final SharedInformerFactoryFactory sharedInformerFactoryFactory;
  private final ApiClientFactory apiClientFactory;

  private final Map<String, WatcherGroup> watchMap; // <id, Watch>

  @Inject
  public K8sWatchServiceDelegate(WatcherFactory watcherFactory,
      SharedInformerFactoryFactory sharedInformerFactoryFactory, ApiClientFactory apiClientFactory) {
    this.watcherFactory = watcherFactory;
    this.sharedInformerFactoryFactory = sharedInformerFactoryFactory;
    this.apiClientFactory = apiClientFactory;
    this.watchMap = new ConcurrentHashMap<>();
  }

  @Value
  @Builder
  public static class WatcherGroup implements Closeable {
    String watchId;
    SharedInformerFactory sharedInformerFactory;
    K8sControllerFetcher controllerFetcher;

    @Override
    public void close() {
      log.info("Closing AllRegisteredInformers for watch {}", watchId);
      sharedInformerFactory.stopAllRegisteredInformers();
    }
  }

  Iterable<String> watchIds() {
    return watchMap.keySet();
  }

  public K8sControllerFetcher getK8sControllerFetcher(String clusterId) {
    WatcherGroup watch = watchMap.get(clusterId);
    return (watch != null) ? watch.controllerFetcher : null;
  }

  public String create(K8sWatchTaskParams params, KubernetesConfig kubernetesConfig) {
    String watchId = params.getClusterId();
    if (K8sWatcherHelper.shouldCreateWatcher(watchId)) {
      log.info("Creating watch with id: {}", watchId);
      ApiClient apiClient = apiClientFactory.getClient(kubernetesConfig);
      DefaultK8sMetricsClient k8sMetricsClient = new DefaultK8sMetricsClient(apiClient);

      String kubeSystemUid = getKubeSystemUid(k8sMetricsClient);

      ClusterDetails clusterDetails =
          ClusterDetails.builder()
              .cloudProviderId(params.getCloudProviderId())
              .clusterId(params.getClusterId())
              .clusterName(params.getClusterName())
              .kubeSystemUid(kubeSystemUid)
              .isSeen(false) // TODO(UTSAV): [TEMP] K8sClusterHelper.isSeen(params.getClusterId(), kubeSystemUid)
              .build();

      SharedInformerFactory sharedInformerFactory =
          sharedInformerFactoryFactory.createSharedInformerFactory(apiClient, clusterDetails);

      PVCFetcher pvcFetcher = watcherFactory.createPVCFetcher(apiClient, sharedInformerFactory);
      SharedInformer<V1PersistentVolumeClaim> pvcInformer =
          sharedInformerFactory.getExistingSharedIndexInformer(V1PersistentVolumeClaim.class);
      pvcInformer.run();

      NamespaceFetcher namespaceFetcher = watcherFactory.createNamespaceFetcher(apiClient, sharedInformerFactory);
      SharedInformer<V1Namespace> namespaceInformer =
          sharedInformerFactory.getExistingSharedIndexInformer(V1Namespace.class);
      namespaceInformer.run();

      Map<String, Store<?>> stores = KNOWN_WORKLOAD_TYPES.entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey, e -> sharedInformerFactory.getExistingSharedIndexInformer(e.getValue()).getIndexer()));

      CrdWorkloadFetcher crdWorkloadFetcher = new CrdWorkloadFetcher(apiClient);
      K8sControllerFetcher controllerFetcher = new K8sControllerFetcher(stores, crdWorkloadFetcher);

      // get seen clusters
      watcherFactory.createNodeWatcher(apiClient, clusterDetails, sharedInformerFactory);
      watcherFactory.createPVWatcher(apiClient, clusterDetails, sharedInformerFactory);

      blockingWaitForFetchersToSync(ImmutableList.of(pvcInformer, namespaceInformer));
      watcherFactory.createPodWatcher(
          apiClient, clusterDetails, controllerFetcher, sharedInformerFactory, pvcFetcher, namespaceFetcher);

      log.info("Starting AllRegisteredInformers for watch {}", watchId);
      sharedInformerFactory.startAllRegisteredInformers();

      // cluster is seen/old now, any new onAdd event older than 2 hours will be ignored.
      // TODO(UTSAV): TEMP K8sClusterHelper.setAsSeen(params.getClusterId(), kubeSystemUid);
      watchMap.put(watchId,
          WatcherGroup.builder()
              .watchId(watchId)
              .sharedInformerFactory(sharedInformerFactory)
              .controllerFetcher(controllerFetcher)
              .build());
    }

    return watchId;
  }

  @SneakyThrows
  private void blockingWaitForFetchersToSync(ImmutableList<SharedInformer<?>> informers) {
    int cnt = 0;
    while (!informers.stream().map(SharedInformer::hasSynced).filter(i -> !i).findFirst().orElse(Boolean.TRUE)
        && ++cnt <= 25) {
      log.info("Waiting for InformerFetchers to sync... {}", cnt);
      Thread.sleep(300);
    }
  }

  public static String getKubeSystemUid(DefaultK8sMetricsClient client) {
    try {
      return client.readNamespace("kube-system", null).getMetadata().getUid();
    } catch (Exception e) {
      log.warn("Error getting kube-system namespace uid", e);
      return "kube-system";
    }
  }

  public void delete(String watchId) {
    watchMap.computeIfPresent(watchId, (id, watcherGroup) -> {
      log.info("Deleting watch with id: {}", watchId);
      K8sWatcherHelper.deleteWatcher(watchId);
      watcherGroup.close();
      return null;
    });
  }
}
