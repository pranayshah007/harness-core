/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class InfrastructurePlanCreatorHelper {
  public List<InfrastructureConfig> getResolvedInfrastructureConfig(
      List<InfrastructureEntity> infrastructureEntityList, Map<String, Map<String, Object>> refToInputMap) {
    List<InfrastructureConfig> infrastructureConfigs = new ArrayList<>();
    for (InfrastructureEntity entity : infrastructureEntityList) {
      String mergedInfraYaml = entity.getYaml();

      if (refToInputMap.containsKey(entity.getIdentifier())) {
        Map<String, Object> infraInputYaml = new HashMap<>();
        infraInputYaml.put(YamlTypes.INFRASTRUCTURE_DEF, refToInputMap.get(entity.getIdentifier()));
        mergedInfraYaml = MergeHelper.mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(
            entity.getYaml(), YamlPipelineUtils.writeYamlString(infraInputYaml), true, true);
      }

      try {
        infrastructureConfigs.add(YamlUtils.read(mergedInfraYaml, InfrastructureConfig.class));
      } catch (IOException e) {
        throw new InvalidRequestException(
            format("Failed to resolve infrastructure inputs %s ", entity.getIdentifier()));
      }
    }
    return infrastructureConfigs;
  }

  public void setInfraIdentifierAndName(
      Infrastructure infrastructure, String infraIdentifier, String infraName, boolean skipInstances) {
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        k8SDirectInfrastructure.setInfraIdentifier(infraIdentifier);
        k8SDirectInfrastructure.setInfraName(infraName);
        return;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        CustomDeploymentInfrastructure customDeploymentInfrastructure = (CustomDeploymentInfrastructure) infrastructure;
        customDeploymentInfrastructure.setInfraIdentifier(infraIdentifier);
        customDeploymentInfrastructure.setInfraName(infraName);
        return;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        k8sGcpInfrastructure.setInfraIdentifier(infraIdentifier);
        k8sGcpInfrastructure.setInfraName(infraName);
        return;

      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        serverlessAwsLambdaInfrastructure.setInfraName(infraName);
        serverlessAwsLambdaInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        k8sAzureInfrastructure.setInfraName(infraName);
        k8sAzureInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
        pdcInfrastructure.setInfraName(infraName);
        pdcInfrastructure.setInfraIdentifier(infraIdentifier);
        pdcInfrastructure.setSkipInstances(skipInstances);
        return;

      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        sshWinRmAwsInfrastructure.setInfraName(infraName);
        sshWinRmAwsInfrastructure.setInfraIdentifier(infraIdentifier);
        sshWinRmAwsInfrastructure.setSkipInstances(skipInstances);
        return;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        sshWinRmAzureInfrastructure.setInfraName(infraName);
        sshWinRmAzureInfrastructure.setInfraIdentifier(infraIdentifier);
        sshWinRmAzureInfrastructure.setSkipInstances(skipInstances);
        return;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        azureWebAppInfrastructure.setInfraName(infraName);
        azureWebAppInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        ecsInfrastructure.setInfraName(infraName);
        ecsInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        GoogleFunctionsInfrastructure googleFunctionsInfrastructure = (GoogleFunctionsInfrastructure) infrastructure;
        googleFunctionsInfrastructure.setInfraName(infraName);
        googleFunctionsInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.ELASTIGROUP:
        ElastigroupInfrastructure elastigroupInfrastructure = (ElastigroupInfrastructure) infrastructure;
        elastigroupInfrastructure.setInfraName(infraName);
        elastigroupInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.TAS:
        TanzuApplicationServiceInfrastructure tanzuApplicationServiceInfrastructure =
            (TanzuApplicationServiceInfrastructure) infrastructure;
        tanzuApplicationServiceInfrastructure.setInfraName(infraName);
        tanzuApplicationServiceInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.ASG:
        AsgInfrastructure asgInfrastructure = (AsgInfrastructure) infrastructure;
        asgInfrastructure.setInfraName(infraName);
        asgInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.AWS_SAM:
        AwsSamInfrastructure awsSamInfrastructure = (AwsSamInfrastructure) infrastructure;
        awsSamInfrastructure.setInfraName(infraName);
        awsSamInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.AWS_LAMBDA:
        AwsLambdaInfrastructure awsLambdaInfrastructure = (AwsLambdaInfrastructure) infrastructure;
        awsLambdaInfrastructure.setInfraName(infraName);
        awsLambdaInfrastructure.setInfraIdentifier(infraIdentifier);
        return;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        k8sAwsInfrastructure.setInfraIdentifier(infraIdentifier);
        k8sAwsInfrastructure.setInfraName(infraName);
        return;

      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }
  }
}