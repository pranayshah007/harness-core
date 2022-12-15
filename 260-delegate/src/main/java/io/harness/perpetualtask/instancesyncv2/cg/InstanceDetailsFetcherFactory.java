/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

@OwnedBy(CDP)
public class InstanceDetailsFetcherFactory {
  private final CgK8sInstancesDetailsFetcher k8sInstancesDetailsFetcher;
  private final ConcurrentHashMap<String, InstanceDetailsFetcher> holder;

  @Inject
  public InstanceDetailsFetcherFactory(CgK8sInstancesDetailsFetcher instanceDetailsFetcher) {
    this.holder = new ConcurrentHashMap<>();
    this.k8sInstancesDetailsFetcher = instanceDetailsFetcher;

    initFetchers();
  }

  private void initFetchers() {
    this.holder.put("DIRECT_KUBERNETES", k8sInstancesDetailsFetcher);
  }

  public InstanceDetailsFetcher getFetcher(String infraMappingType) {
    return this.holder.getOrDefault(infraMappingType, null);
  }
}
