/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.SSH;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.api.DeploymentType;
import software.wings.infra.InfrastructureDefinition;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class InfraMapperFactory {
  private static final UnsupportedInfraDefMapper unsupportedInfraDefMapper = new UnsupportedInfraDefMapper();
  private static final InfraDefMapper k8sInfraDefMapper = new K8sInfraDefMapper();
  private static final InfraDefMapper helmInfraDefMapper = new NativeHelmInfraDefMapper();
  private static final InfraDefMapper sshInfraDefMapper = new SshInfraDefMapper();

  public static final Map<DeploymentType, InfraDefMapper> INFRA_DEF_MAPPER_MAP =
      ImmutableMap.<DeploymentType, InfraDefMapper>builder()
          .put(KUBERNETES, k8sInfraDefMapper)
          .put(HELM, helmInfraDefMapper)
          .put(SSH, sshInfraDefMapper)
          .build();

  public static InfraDefMapper getInfraDefMapper(InfrastructureDefinition infraDef) {
    DeploymentType deploymentType = infraDef.getDeploymentType();
    return INFRA_DEF_MAPPER_MAP.getOrDefault(deploymentType, unsupportedInfraDefMapper);
  }
}
