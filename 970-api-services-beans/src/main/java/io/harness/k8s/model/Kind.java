/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidArgumentsException;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public enum Kind {
  Namespace,
  ResourceQuota,
  LimitRange,
  PodSecurityPolicy,
  Secret,
  ConfigMap,
  StorageClass,
  PersistentVolume,
  PersistentVolumeClaim,
  ServiceAccount,
  CustomResourceDefinition,
  ClusterRole,
  ClusterRoleBinding,
  Role,
  RoleBinding,
  Service,
  DaemonSet,
  Pod,
  ReplicationController,
  ReplicaSet,
  Deployment,
  DeploymentConfig,
  StatefulSet,
  Job,
  CronJob,
  Ingress,
  APIService,
  VirtualService,
  DestinationRule,
  HorizontalPodAutoscaler,
  PodDisruptionBudget,
  NOOP;

  public static final Set<String> BG_STAGE_SCALE_DOWN_WORKLOAD_KINDS =
      ImmutableSet.of(Deployment.name(), StatefulSet.name(), DaemonSet.name(), DeploymentConfig.name());
  public static final Set<String> BG_STAGE_DELETE_WORKLOAD_KINDS =
      ImmutableSet.of(HorizontalPodAutoscaler.name(), PodDisruptionBudget.name());
  public static final Set<String> BG_STAGE_WORKLOAD_KINDS =
      SetUtils.union(BG_STAGE_SCALE_DOWN_WORKLOAD_KINDS, BG_STAGE_DELETE_WORKLOAD_KINDS);
  public static final Set<String> SCALABLE_WORKLOAD_KINDS =
      ImmutableSet.of(Deployment.name(), DaemonSet.name(), StatefulSet.name(), DeploymentConfig.name(),
          ReplicationController.name(), ReplicaSet.name(), Job.name(), CronJob.name());

  public static Kind fromString(String kindName) {
    if (isEmpty(kindName)) {
      throw new InvalidArgumentsException(Pair.of("kind", "Empty or null kind provided"));
    }

    for (Kind kind : Kind.values()) {
      if (kindName.equalsIgnoreCase(kind.name())) {
        return kind;
      }
    }

    throw new InvalidArgumentsException(
        Pair.of("kind", String.format("Unsupported or unknown kubernetes kind '%s'", kindName)));
  }
}
