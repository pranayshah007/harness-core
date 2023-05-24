/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.exception.InvalidArgumentsException;

import org.apache.commons.lang3.tuple.Pair;

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
