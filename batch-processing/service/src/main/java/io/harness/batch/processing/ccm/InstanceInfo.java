/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.ccm;

import io.harness.ccm.HarnessServiceInfoNG;
import io.harness.ccm.commons.beans.Container;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.beans.StorageResource;
import io.harness.data.structure.MongoMapSanitizer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.PrePersist;

@Data
@Builder
public class InstanceInfo {
  String accountId;
  String settingId;
  String instanceId;
  String clusterId;
  String clusterName;
  String instanceName;
  String cloudProviderInstanceId;
  InstanceType instanceType;
  InstanceState instanceState;
  Instant usageStartTime;
  Resource resource;
  Resource resourceLimit;
  Resource allocatableResource;
  Resource pricingResource;
  StorageResource storageResource;
  List<Container> containerList;
  Map<String, String> labels;
  Map<String, String> namespaceLabels;
  Map<String, String> metaData;
  Map<String, String> metadataAnnotations;
  List<String> pvcClaimNames;
  HarnessServiceInfo harnessServiceInfo;
  HarnessServiceInfoNG harnessServiceInfoNg;

  private static final MongoMapSanitizer SANITIZER = new MongoMapSanitizer('~');

  @PrePersist
  void prePersist() {
    metadataAnnotations = SANITIZER.encodeDotsInKey(metadataAnnotations);
  }

  @PostLoad
  void postLoad() {
    metadataAnnotations = SANITIZER.decodeDotsInKey(metadataAnnotations);
  }
}
