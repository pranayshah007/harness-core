/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.informer;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.client.EventPublisher;
import io.harness.perpetualtask.k8s.informer.handlers.V1DaemonSetHandler;
import io.harness.perpetualtask.k8s.informer.handlers.V1DeploymentHandler;
import io.harness.perpetualtask.k8s.informer.handlers.V1JobHandler;
import io.harness.perpetualtask.k8s.informer.handlers.V1ReplicaSetHandler;
import io.harness.perpetualtask.k8s.informer.handlers.V1StatefulSetHandler;
import io.harness.perpetualtask.k8s.informer.handlers.V1beta1CronJobHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.BatchV1beta1Api;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetList;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetList;
import io.kubernetes.client.openapi.models.V1beta1CronJob;
import io.kubernetes.client.openapi.models.V1beta1CronJobList;
import io.kubernetes.client.util.CallGeneratorParams;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Singleton
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class SharedInformerFactoryFactory {
  private final EventPublisher eventPublisher;

  @Inject
  public SharedInformerFactoryFactory(EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public SharedInformerFactory createSharedInformerFactory(ApiClient apiClient, ClusterDetails clusterDetails) {
    log.info("Creating new SharedInformerFactory for cluster: {}", clusterDetails.getClusterId());
    SharedInformerFactory factory = new SharedInformerFactory(apiClient);
    addHandlerForReplicaSet(factory, apiClient, clusterDetails);
    addHandlerForDeployment(factory, apiClient, clusterDetails);
    addHandlerForDaemonset(factory, apiClient, clusterDetails);
    addHandlerForStatefulSet(factory, apiClient, clusterDetails);
    addHandlerForJob(factory, apiClient, clusterDetails);
    addHandlerForCronJob(factory, apiClient, clusterDetails);
    return factory;
  }

  private void addHandlerForCronJob(SharedInformerFactory factory, ApiClient apiClient, ClusterDetails clusterDetails) {
    BatchV1beta1Api batchV1beta1Api = new BatchV1beta1Api(apiClient);
    factory
        .sharedIndexInformerFor((CallGeneratorParams params)
                                    -> batchV1beta1Api.listCronJobForAllNamespacesCall(null, null, null, null, null,
                                        null, params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
            V1beta1CronJob.class, V1beta1CronJobList.class)
        .addEventHandler(new V1beta1CronJobHandler(eventPublisher, clusterDetails));
  }

  private void addHandlerForJob(SharedInformerFactory factory, ApiClient apiClient, ClusterDetails clusterDetails) {
    BatchV1Api batchV1Api = new BatchV1Api(apiClient);
    factory
        .sharedIndexInformerFor((CallGeneratorParams params)
                                    -> batchV1Api.listJobForAllNamespacesCall(null, null, null, null, null, null,
                                        params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
            V1Job.class, V1JobList.class)
        .addEventHandler(new V1JobHandler(eventPublisher, clusterDetails));
  }

  private void addHandlerForStatefulSet(
      SharedInformerFactory factory, ApiClient apiClient, ClusterDetails clusterDetails) {
    AppsV1Api appsV1Api = new AppsV1Api(apiClient);
    factory
        .sharedIndexInformerFor((CallGeneratorParams params)
                                    -> appsV1Api.listStatefulSetForAllNamespacesCall(null, null, null, null, null, null,
                                        params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
            V1StatefulSet.class, V1StatefulSetList.class)
        .addEventHandler(new V1StatefulSetHandler(eventPublisher, clusterDetails));
  }

  private void addHandlerForDaemonset(
      SharedInformerFactory factory, ApiClient apiClient, ClusterDetails clusterDetails) {
    AppsV1Api appsV1Api = new AppsV1Api(apiClient);
    factory
        .sharedIndexInformerFor((CallGeneratorParams params)
                                    -> appsV1Api.listDaemonSetForAllNamespacesCall(null, null, null, null, null, null,
                                        params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
            V1DaemonSet.class, V1DaemonSetList.class)
        .addEventHandler(new V1DaemonSetHandler(eventPublisher, clusterDetails));
  }

  private void addHandlerForDeployment(
      SharedInformerFactory factory, ApiClient apiClient, ClusterDetails clusterDetails) {
    AppsV1Api appsV1Api = new AppsV1Api(apiClient);
    factory
        .sharedIndexInformerFor((CallGeneratorParams params)
                                    -> appsV1Api.listDeploymentForAllNamespacesCall(null, null, null, null, null, null,
                                        params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
            V1Deployment.class, V1DeploymentList.class)
        .addEventHandler(new V1DeploymentHandler(eventPublisher, clusterDetails));
  }

  private void addHandlerForReplicaSet(
      SharedInformerFactory factory, ApiClient apiClient, ClusterDetails clusterDetails) {
    AppsV1Api appsV1Api = new AppsV1Api(apiClient);
    factory
        .sharedIndexInformerFor((CallGeneratorParams params)
                                    -> appsV1Api.listReplicaSetForAllNamespacesCall(null, null, null, null, null, null,
                                        params.resourceVersion, null, params.timeoutSeconds, params.watch, null),
            V1ReplicaSet.class, V1ReplicaSetList.class)
        .addEventHandler(new V1ReplicaSetHandler(eventPublisher, clusterDetails));
  }
}
