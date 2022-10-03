/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;

@OwnedBy(HarnessTeam.CDC)
public class ServiceV2Factory {
  private static final ServiceV2Mapper k8sServiceV2Mapper = new K8sServiceV2Mapper();
  private static final ServiceV2Mapper nativeHelmServiceV2Mapper = new NativeHelmServiceV2Mapper();

  public static ServiceV2Mapper getService2Mapper(Service service) {
    if (DeploymentType.KUBERNETES.equals(service.getDeploymentType())) {
      return k8sServiceV2Mapper;
    }
    if (DeploymentType.HELM.equals(service.getDeploymentType())) {
      return nativeHelmServiceV2Mapper;
    }
    throw new InvalidRequestException(
        String.format("Service of deployment type %s supported", service.getDeploymentType()));
  }
}
