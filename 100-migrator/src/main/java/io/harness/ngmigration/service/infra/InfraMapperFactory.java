/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static software.wings.api.DeploymentType.AMI;
import static software.wings.api.DeploymentType.AWS_LAMBDA;
import static software.wings.api.DeploymentType.AZURE_WEBAPP;
import static software.wings.api.DeploymentType.CUSTOM;
import static software.wings.api.DeploymentType.ECS;
import static software.wings.api.DeploymentType.HELM;
import static software.wings.api.DeploymentType.KUBERNETES;
import static software.wings.api.DeploymentType.PCF;
import static software.wings.api.DeploymentType.SSH;
import static software.wings.api.DeploymentType.WINRM;

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
  private static final InfraDefMapper sshWinRmInfraDefMapper = new SshWinRmInfraDefMapper();
  private static final InfraDefMapper ecsInfraDefMapper = new EcsInfraDefMapper();
  private static final InfraDefMapper elastigroupInfraDefMapper = new AmiElastigroupInfraDefMapper();
  private static final InfraDefMapper customDeploymentInfraDefMapper = new CustomDeploymentInfraDefMapper();
  private static final InfraDefMapper pcfInfraDefMapper = new PcfInfraDefMapper();
  private static final InfraDefMapper azureWebappInfraDefMapper = new AzureWebappInfraDefMapper();
  private static final InfraDefMapper awsLambdaInfraDefMapper = new AwsLambdaInfraDefMapper();
  public static final Map<DeploymentType, InfraDefMapper> INFRA_DEF_MAPPER_MAP =
      ImmutableMap.<DeploymentType, InfraDefMapper>builder()
          .put(KUBERNETES, k8sInfraDefMapper)
          .put(HELM, helmInfraDefMapper)
          .put(SSH, sshWinRmInfraDefMapper)
          .put(ECS, ecsInfraDefMapper)
          .put(AMI, elastigroupInfraDefMapper)
          .put(CUSTOM, customDeploymentInfraDefMapper)
          .put(WINRM, sshWinRmInfraDefMapper)
          .put(PCF, pcfInfraDefMapper)
          .put(AZURE_WEBAPP, azureWebappInfraDefMapper)
          .put(AWS_LAMBDA, awsLambdaInfraDefMapper)
          .build();

  public static InfraDefMapper getInfraDefMapper(InfrastructureDefinition infraDef) {
    DeploymentType deploymentType = infraDef.getDeploymentType();
    return INFRA_DEF_MAPPER_MAP.getOrDefault(deploymentType, unsupportedInfraDefMapper);
  }
}
