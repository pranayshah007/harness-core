/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config.k8s.resource.change;

import io.harness.ccm.commons.entities.k8s.K8sYaml;
import io.harness.perpetualtask.k8s.watch.K8sWatchEvent;

import lombok.Value;

@Value(staticConstructor = "of")
public class WorkloadEventId {
  String oldYamlRef;
  String newYamlRef;

  public static WorkloadEventId of(String accountId, K8sWatchEvent k8sWatchEvent) {
    String clusterId = k8sWatchEvent.getClusterId();
    String uid = k8sWatchEvent.getResourceRef().getUid();
    String oldYaml = k8sWatchEvent.getOldResourceYaml();
    String newYaml = k8sWatchEvent.getNewResourceYaml();
    String oldYamlRef = K8sYaml.hash(accountId, clusterId, uid, oldYaml);
    String newYamlRef = K8sYaml.hash(accountId, clusterId, uid, newYaml);
    return WorkloadEventId.of(oldYamlRef, newYamlRef);
  }
}
