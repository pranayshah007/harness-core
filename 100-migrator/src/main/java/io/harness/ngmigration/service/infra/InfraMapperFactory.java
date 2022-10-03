/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import software.wings.infra.InfrastructureDefinition;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDC)
public class InfraMapperFactory {
  @Inject K8sInfraDefMapper k8sInfraDefMapper;
  @Inject NativeHelmInfraDefMapper helmInfraDefMapper;

  public InfraDefMapper getInfraDefMapper(InfrastructureDefinition infraDef) {
    switch (infraDef.getDeploymentType()) {
      case KUBERNETES:
        return k8sInfraDefMapper;
      case HELM:
        return helmInfraDefMapper;
      default:
        throw new InvalidRequestException("Unsupported Infrastructure");
    }
  }
}
