/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer.constants;

public class K8sCCMConstants {
  public static final String AWS_LIFECYCLE_KEY = "lifecycle";
  public static final String AWS_CAPACITY_TYPE_KEY = "capacityType";
  public static final String AZURE_LIFECYCLE_KEY = "kubernetes.azure.com/scalesetpriority";
  public static final String RELEASE_NAME = "harness.io/release-name";
  public static final String HELM_RELEASE_NAME = "release";
  public static final String K8SV1_RELEASE_NAME = "harness.io/service-infra-id";
  public static final String OPERATING_SYSTEM = "beta.kubernetes.io/os";
  public static final String GKE_SPOT_KEY = "cloud.google.com/gke-spot";
  public static final String GKE_PREEMPTIBLE_KEY = "cloud.google.com/gke-preemptible";
  public static final String PREEMPTIBLE_KEY = "preemptible";
  public static final String PREEMPTIBLE_NODE_KEY = "preemptible-node";
  public static final String REGION = "failure-domain.beta.kubernetes.io/region";
  public static final String ZONE = "failure-domain.beta.kubernetes.io/zone";
  public static final String INSTANCE_FAMILY = "beta.kubernetes.io/instance-type";
  public static final String GKE_NODE_POOL_KEY = "cloud.google.com/gke-nodepool";
  public static final String AKS_NODE_POOL_KEY = "agentpool";
  public static final String EKSCTL_NODE_POOL_KEY = "alpha.eksctl.io/nodegroup-name";
  public static final String EKS_NODE_POOL_KEY = "eks.amazonaws.com/nodegroup";
  public static final String KOPS_NODE_POOL_KEY = "kops.k8s.io/instancegroup";
  public static final String GENERAL_NODE_POOL_KEY = "node-pool-name";
  public static final String GENERAL_NODE_GROUP_KEY = "nodegroup";
  public static final String COMPUTE_TYPE = "eks.amazonaws.com/compute-type";
  public static final String UNALLOCATED = "Unallocated";
  public static final String DEFAULT_DEPLOYMENT_TYPE = "Pod";
  public static final String AWS_FARGATE_COMPUTE_TYPE = "fargate";
  public static final String NODE_POOL_NAME_KEY = "node-pool-name";
  public static final String SPOT_INSTANCE_NODE_LIFECYCLE = "spotinst~io/node-lifecycle";
  public static final String SPOT_INSTANCE = "spot";
  public static final String ON_DEMAND_INSTANCE = "od";

  public static final String VIRTUAL_KUBELET = "virtual-kubelet";

  private K8sCCMConstants() {}
}
