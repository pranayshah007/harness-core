/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.utils.ArtifactType;

import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class ServiceV2Factory {
  private static final ServiceV2Mapper k8sServiceV2Mapper = new K8sServiceV2Mapper();
  private static final ServiceV2Mapper sshServiceV2Mapper = new SshServiceV2Mapper();
  private static final ServiceV2Mapper winrmServiceV2Mapper = new WinrmServiceV2Mapper();
  private static final ServiceV2Mapper nativeHelmServiceV2Mapper = new NativeHelmServiceV2Mapper();
  private static final ServiceV2Mapper ecsServiceV2Mapper = new EcsServiceV2Mapper();
  private static final ServiceV2Mapper azureWebappServiceV2Mapper = new AzureWebappServiceV2Mapper();
  private static final ServiceV2Mapper elastigroupServiceV2Mapper = new ElastigroupServiceV2Mapper();
  private static final ServiceV2Mapper pcfServiceV2Mapper = new PcfServiceV2Mapper();
  private static final ServiceV2Mapper customDeploymentServiceV2Mapper = new CustomDeploymentServiceV2Mapper();
  private static final ServiceV2Mapper unsupportedServiceV2Mapper = new UnsupportedServiceV2Mapper();

  public static ServiceV2Mapper getService2Mapper(Service service, boolean ecsTask) {
    DeploymentType deploymentType = service.getDeploymentType();
    ArtifactType artifactType = service.getArtifactType();
    return getServiceV2Mapper(deploymentType, artifactType, ecsTask);
  }

  @NotNull
  public static ServiceV2Mapper getServiceV2Mapper(
      DeploymentType deploymentType, ArtifactType artifactType, boolean ecsTask) {
    if (DeploymentType.KUBERNETES.equals(deploymentType)) {
      return k8sServiceV2Mapper;
    }
    if (DeploymentType.HELM.equals(deploymentType)) {
      return nativeHelmServiceV2Mapper;
    }
    if (DeploymentType.SSH.equals(deploymentType)) {
      return sshServiceV2Mapper;
    }
    if (DeploymentType.WINRM.equals(deploymentType)) {
      return winrmServiceV2Mapper;
    }
    if (DeploymentType.ECS.equals(deploymentType)) {
      return ecsServiceV2Mapper;
    }
    if (DeploymentType.AZURE_WEBAPP.equals(deploymentType)) {
      return azureWebappServiceV2Mapper;
    }
    if (DeploymentType.AMI.equals(deploymentType)) {
      return elastigroupServiceV2Mapper;
    }
    if (DeploymentType.PCF.equals(deploymentType)) {
      return pcfServiceV2Mapper;
    }
    if (DeploymentType.CUSTOM.equals(deploymentType)) {
      return customDeploymentServiceV2Mapper;
    }
    if (null == deploymentType && null != artifactType) {
      switch (artifactType) {
        case AMI:
          return elastigroupServiceV2Mapper;
        case IIS:
        case IIS_APP:
        case IIS_VirtualDirectory:
          return winrmServiceV2Mapper;
        case JAR:
        case WAR:
        case RPM:
        case ZIP:
        case TAR:
        case OTHER:
        case NUGET:
          return sshServiceV2Mapper;
        case PCF:
          return pcfServiceV2Mapper;
        case DOCKER:
          if (ecsTask) {
            return ecsServiceV2Mapper;
          } else {
            return k8sServiceV2Mapper;
          }
        case AZURE_WEBAPP:
          return azureWebappServiceV2Mapper;
        case AWS_CODEDEPLOY:
        case AWS_LAMBDA:
        case AZURE_MACHINE_IMAGE:
          return unsupportedServiceV2Mapper;
        default:
          return unsupportedServiceV2Mapper;
      }
    }
    return unsupportedServiceV2Mapper;
  }
}
