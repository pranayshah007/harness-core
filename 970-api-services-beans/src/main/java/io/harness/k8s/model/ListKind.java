/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public enum ListKind {
  List(null),
  NamespaceList(Kind.Namespace),
  ServiceList(Kind.Service),
  DeploymentList(Kind.Deployment),
  RoleBindingList(Kind.RoleBinding),
  ClusterRoleBindingList(Kind.ClusterRoleBinding),
  ConfigMapList(Kind.ConfigMap),
  SecretList(Kind.Secret),
  ResourceQuotaList(Kind.ResourceQuota),
  LimitRangeList(Kind.LimitRange),
  StorageClassList(Kind.StorageClass),
  PersistentVolumeList(Kind.PersistentVolume),
  PersistentVolumeClaimList(Kind.PersistentVolumeClaim),
  ServiceAccountList(Kind.ServiceAccount),
  CustomResourceDefinitionList(Kind.CustomResourceDefinition),
  ClusterRoleList(Kind.ClusterRole),
  RoleList(Kind.Role),
  DaemonSetList(Kind.DaemonSet),
  PodList(Kind.Pod),
  ReplicationControllerList(Kind.ReplicationController),
  ReplicaSetList(Kind.ReplicaSet),
  StatefulSetList(Kind.StatefulSet),
  JobList(Kind.Job),
  CronJobList(Kind.CronJob),
  IngressList(Kind.Ingress),
  APIServiceList(Kind.APIService),
  HorizontalPodAutoscalerList(Kind.HorizontalPodAutoscaler),
  PodDisruptionBudgetList(Kind.PodDisruptionBudget);

  private final Kind itemKind;

  ListKind(Kind itemKind) {
    this.itemKind = itemKind;
  }

  public Kind getItemKind() {
    return itemKind;
  }
}
