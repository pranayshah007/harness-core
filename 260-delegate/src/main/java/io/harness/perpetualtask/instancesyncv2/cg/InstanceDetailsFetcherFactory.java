/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;

import software.wings.utils.Utils;

import com.google.inject.Inject;

@OwnedBy(CDP)
public class InstanceDetailsFetcherFactory {
  private final CgK8sInstancesDetailsFetcher k8sInstancesDetailsFetcher;
  private final CgAwsSshInstancesDetailsFetcher awsSshInstancesDetailsFetcher;

  @Inject
  public InstanceDetailsFetcherFactory(CgK8sInstancesDetailsFetcher instanceDetailsFetcher,
      CgAwsSshInstancesDetailsFetcher awsSshInstancesDetailsFetcher) {
    this.awsSshInstancesDetailsFetcher = awsSshInstancesDetailsFetcher;
    this.k8sInstancesDetailsFetcher = instanceDetailsFetcher;
  }

  public InstanceDetailsFetcher getFetcher(String infraMapping) {
    /*    InfrastructureMappingType infraMappingType = Utils.getEnumFromString(InfrastructureMappingType.class,
       infraMapping); switch (infraMappingType) { case DIRECT_KUBERNETES: return k8sInstancesDetailsFetcher; case
       AWS_SSH: return awsSshInstancesDetailsFetcher; default: throw new UnexpectedException("No handler defined for
       infra mapping type: " + infraMappingType);
        }*/
    return awsSshInstancesDetailsFetcher;
  }
}
