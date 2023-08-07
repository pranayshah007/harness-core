/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceDefinitionType;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.utils.ArtifactType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class ServiceV2Factory {
  private static final ServiceV2Mapper k8sServiceV2Mapper = new K8sServiceV2Mapper();
  private static final ServiceV2Mapper sshServiceV2Mapper = new SshServiceV2Mapper();
  private static final ServiceV2Mapper winrmServiceV2Mapper = new WinrmServiceV2Mapper();
  private static final ServiceV2Mapper nativeHelmServiceV2Mapper = new NativeHelmServiceV2Mapper();
  private static final ServiceV2Mapper ecsServiceV2Mapper = new EcsServiceV2Mapper();
  private static final ServiceV2Mapper azureWebappServiceV2Mapper = new AzureWebappServiceV2Mapper();
  private static final ServiceV2Mapper elastigroupServiceV2Mapper = new AmiServiceV2Mapper();
  private static final ServiceV2Mapper pcfServiceV2Mapper = new PcfServiceV2Mapper();
  private static final ServiceV2Mapper customDeploymentServiceV2Mapper = new CustomDeploymentServiceV2Mapper();
  private static final ServiceV2Mapper awsLambdaServiceV2Mapper = new AwsLambdaServiceV2Mapper();
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
    if (DeploymentType.AWS_LAMBDA.equals(deploymentType)) {
      return awsLambdaServiceV2Mapper;
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
        case AWS_LAMBDA:
          return awsLambdaServiceV2Mapper;
        case AWS_CODEDEPLOY:
        case AZURE_MACHINE_IMAGE:
          return unsupportedServiceV2Mapper;
        default:
          return unsupportedServiceV2Mapper;
      }
    }
    return unsupportedServiceV2Mapper;
  }

  public static ServiceDefinitionType mapDeploymentTypeToServiceDefType(DeploymentType deploymentType) {
    Map<DeploymentType, ServiceDefinitionType> map = new HashMap<>();
    map.put(DeploymentType.SSH, ServiceDefinitionType.SSH);
    map.put(DeploymentType.ECS, ServiceDefinitionType.ECS);
    map.put(DeploymentType.SPOTINST, ServiceDefinitionType.ELASTIGROUP);
    map.put(DeploymentType.KUBERNETES, ServiceDefinitionType.KUBERNETES);
    map.put(DeploymentType.HELM, ServiceDefinitionType.NATIVE_HELM);
    map.put(DeploymentType.AWS_LAMBDA, ServiceDefinitionType.AWS_LAMBDA);
    map.put(DeploymentType.AMI, ServiceDefinitionType.ASG);
    map.put(DeploymentType.WINRM, ServiceDefinitionType.WINRM);
    map.put(DeploymentType.PCF, ServiceDefinitionType.TAS);
    map.put(DeploymentType.AZURE_WEBAPP, ServiceDefinitionType.AZURE_WEBAPP);
    map.put(DeploymentType.CUSTOM, ServiceDefinitionType.CUSTOM_DEPLOYMENT);
    //    map.put(DeploymentType.AZURE_VMSS, ServiceDefinitionType.);
    //    map.put(AWS_CODEDEPLOY, ServiceDefinitionType.);
    return map.get(deploymentType);
  }

  public static boolean checkForASG(String nodeType) {
    Set<String> set = new HashSet<>();
    set.add("AWS_AMI_SERVICE_SETUP");
    set.add("AWS_AMI_SERVICE_ROLLBACK");
    set.add("AWS_AMI_SWITCH_ROUTES");
    set.add("AWS_AMI_ROLLBACK_SWITCH_ROUTES");
    set.add("AWS_AMI_SERVICE_DEPLOY");
    set.add("ASG_AMI_ALB_SHIFT_SWITCH_ROUTES");
    set.add("ASG_AMI_SERVICE_ALB_SHIFT_DEPLOY");
    set.add("ASG_AMI_SERVICE_ALB_SHIFT_SETUP");
    set.add("ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES");
    return set.contains(nodeType);
  }
}
