/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.cdng.infra.beans.host.dto.HostFilterSpecDTO.HOSTS_SEPARATOR;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.beans.AsgInfrastructureOutcome;
import io.harness.cdng.infra.beans.AwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.GoogleFunctionsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcomeAbstract;
import io.harness.cdng.infra.beans.K8sAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sRancherInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostAttributesFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.cdng.infra.beans.host.HostNamesFilter;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostAttributesFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostNamesFilterDTO;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.K8sRancherInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.environment.EnvironmentOutcome;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDP)
public class InfrastructureMapper {
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @NotNull
  public InfrastructureOutcome toOutcome(@Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome,
      ServiceStepOutcome service, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    final InfrastructureOutcomeAbstract infrastructureOutcome;
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        K8sDirectInfrastructureOutcome k8SDirectInfrastructureOutcome =
            K8sDirectInfrastructureOutcome.builder()
                .connectorRef(k8SDirectInfrastructure.getConnectorRef().getValue())
                .namespace(k8SDirectInfrastructure.getNamespace().getValue())
                .releaseName(getValueOrExpression(k8SDirectInfrastructure.getReleaseName()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, k8SDirectInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(k8SDirectInfrastructureOutcome, k8SDirectInfrastructure.getInfraIdentifier(),
            k8SDirectInfrastructure.getInfraName());
        infrastructureOutcome = k8SDirectInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        K8sGcpInfrastructureOutcome k8sGcpInfrastructureOutcome =
            K8sGcpInfrastructureOutcome.builder()
                .connectorRef(k8sGcpInfrastructure.getConnectorRef().getValue())
                .namespace(k8sGcpInfrastructure.getNamespace().getValue())
                .cluster(k8sGcpInfrastructure.getCluster().getValue())
                .releaseName(getValueOrExpression(k8sGcpInfrastructure.getReleaseName()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, k8sGcpInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(k8sGcpInfrastructureOutcome, k8sGcpInfrastructure.getInfraIdentifier(),
            k8sGcpInfrastructure.getInfraName());
        infrastructureOutcome = k8sGcpInfrastructureOutcome;
        break;

      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome =
            ServerlessAwsLambdaInfrastructureOutcome.builder()
                .connectorRef(serverlessAwsLambdaInfrastructure.getConnectorRef().getValue())
                .region(serverlessAwsLambdaInfrastructure.getRegion().getValue())
                .stage(serverlessAwsLambdaInfrastructure.getStage().getValue())
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, serverlessAwsLambdaInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(serverlessAwsLambdaInfrastructureOutcome,
            serverlessAwsLambdaInfrastructure.getInfraIdentifier(), serverlessAwsLambdaInfrastructure.getInfraName());
        infrastructureOutcome = serverlessAwsLambdaInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        K8sAzureInfrastructureOutcome k8sAzureInfrastructureOutcome =
            K8sAzureInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(k8sAzureInfrastructure.getConnectorRef()))
                .namespace(getParameterFieldValue(k8sAzureInfrastructure.getNamespace()))
                .cluster(getParameterFieldValue(k8sAzureInfrastructure.getCluster()))
                .releaseName(getValueOrExpression(k8sAzureInfrastructure.getReleaseName()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, k8sAzureInfrastructure.getInfrastructureKeyValues()))
                .subscription(getParameterFieldValue(k8sAzureInfrastructure.getSubscriptionId()))
                .resourceGroup(getParameterFieldValue(k8sAzureInfrastructure.getResourceGroup()))
                .useClusterAdminCredentials(ParameterFieldHelper.getBooleanParameterFieldValue(
                    k8sAzureInfrastructure.getUseClusterAdminCredentials()))
                .build();
        setInfraIdentifierAndName(k8sAzureInfrastructureOutcome, k8sAzureInfrastructure.getInfraIdentifier(),
            k8sAzureInfrastructure.getInfraName());
        infrastructureOutcome = k8sAzureInfrastructureOutcome;
        break;

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
        setPdcInfrastructureHostValueSplittingStringToListIfNeeded(pdcInfrastructure);
        PdcInfrastructureOutcome pdcInfrastructureOutcome =
            PdcInfrastructureOutcome.builder()
                .credentialsRef(getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
                .hosts(getParameterFieldValue(pdcInfrastructure.getHosts()))
                .connectorRef(getParameterFieldValue(pdcInfrastructure.getConnectorRef()))
                .hostFilter(toHostFilterDTO(pdcInfrastructure.getHostFilter()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, pdcInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(
            pdcInfrastructureOutcome, pdcInfrastructure.getInfraIdentifier(), pdcInfrastructure.getInfraName());
        infrastructureOutcome = pdcInfrastructureOutcome;
        break;

      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        SshWinRmAwsInfrastructureOutcome sshWinRmAwsInfrastructureOutcome =
            SshWinRmAwsInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(sshWinRmAwsInfrastructure.getConnectorRef()))
                .credentialsRef(getParameterFieldValue(sshWinRmAwsInfrastructure.getCredentialsRef()))
                .region(getParameterFieldValue(sshWinRmAwsInfrastructure.getRegion()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, infrastructure.getInfrastructureKeyValues()))
                .tags(getParameterFieldValue(sshWinRmAwsInfrastructure.getAwsInstanceFilter().getTags()))
                .hostConnectionType(getParameterFieldValue(sshWinRmAwsInfrastructure.getHostConnectionType()))
                .build();

        setInfraIdentifierAndName(sshWinRmAwsInfrastructureOutcome, sshWinRmAwsInfrastructure.getInfraIdentifier(),
            sshWinRmAwsInfrastructure.getInfraName());
        infrastructureOutcome = sshWinRmAwsInfrastructureOutcome;
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        SshWinRmAzureInfrastructureOutcome sshWinRmAzureInfrastructureOutcome =
            SshWinRmAzureInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(sshWinRmAzureInfrastructure.getConnectorRef()))
                .subscriptionId(getParameterFieldValue(sshWinRmAzureInfrastructure.getSubscriptionId()))
                .resourceGroup(getParameterFieldValue(sshWinRmAzureInfrastructure.getResourceGroup()))
                .credentialsRef(getParameterFieldValue(sshWinRmAzureInfrastructure.getCredentialsRef()))
                .tags(getParameterFieldValue(sshWinRmAzureInfrastructure.getTags()))
                .hostConnectionType(getParameterFieldValue(sshWinRmAzureInfrastructure.getHostConnectionType()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, sshWinRmAzureInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(sshWinRmAzureInfrastructureOutcome, sshWinRmAzureInfrastructure.getInfraIdentifier(),
            sshWinRmAzureInfrastructure.getInfraName());
        infrastructureOutcome = sshWinRmAzureInfrastructureOutcome;
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        AzureWebAppInfrastructureOutcome azureWebAppInfrastructureOutcome =
            AzureWebAppInfrastructureOutcome.builder()
                .connectorRef(azureWebAppInfrastructure.getConnectorRef().getValue())
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, azureWebAppInfrastructure.getInfrastructureKeyValues()))
                .subscription(azureWebAppInfrastructure.getSubscriptionId().getValue())
                .resourceGroup(azureWebAppInfrastructure.getResourceGroup().getValue())
                .build();
        setInfraIdentifierAndName(azureWebAppInfrastructureOutcome, azureWebAppInfrastructure.getInfraIdentifier(),
            azureWebAppInfrastructure.getInfraName());
        infrastructureOutcome = azureWebAppInfrastructureOutcome;
        break;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        EcsInfrastructureOutcome ecsInfrastructureOutcome =
            EcsInfrastructureOutcome.builder()
                .connectorRef(ecsInfrastructure.getConnectorRef().getValue())
                .environment(environmentOutcome)
                .region(ecsInfrastructure.getRegion().getValue())
                .cluster(ecsInfrastructure.getCluster().getValue())
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, ecsInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(
            ecsInfrastructureOutcome, ecsInfrastructure.getInfraIdentifier(), ecsInfrastructure.getInfraName());
        infrastructureOutcome = ecsInfrastructureOutcome;
        break;

      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        GoogleFunctionsInfrastructure googleFunctionsInfrastructure = (GoogleFunctionsInfrastructure) infrastructure;
        GoogleFunctionsInfrastructureOutcome googleFunctionsInfrastructureOutcome =
            GoogleFunctionsInfrastructureOutcome.builder()
                .connectorRef(googleFunctionsInfrastructure.getConnectorRef().getValue())
                .environment(environmentOutcome)
                .region(googleFunctionsInfrastructure.getRegion().getValue())
                .project(googleFunctionsInfrastructure.getProject().getValue())
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, googleFunctionsInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(googleFunctionsInfrastructureOutcome,
            googleFunctionsInfrastructure.getInfraIdentifier(), googleFunctionsInfrastructure.getInfraName());
        infrastructureOutcome = googleFunctionsInfrastructureOutcome;
        break;

      case InfrastructureKind.ELASTIGROUP:
        ElastigroupInfrastructure elastigroupInfrastructure = (ElastigroupInfrastructure) infrastructure;
        ElastigroupInfrastructureOutcome elastigroupInfrastructureOutcome =
            ElastigroupInfrastructureOutcome.builder()
                .connectorRef(elastigroupInfrastructure.getConnectorRef().getValue())
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, elastigroupInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(elastigroupInfrastructureOutcome, elastigroupInfrastructure.getInfraIdentifier(),
            elastigroupInfrastructure.getInfraName());
        infrastructureOutcome = elastigroupInfrastructureOutcome;
        break;

      case InfrastructureKind.ASG:
        AsgInfrastructure asgInfrastructure = (AsgInfrastructure) infrastructure;
        AsgInfrastructureOutcome asgInfrastructureOutcome =
            AsgInfrastructureOutcome.builder()
                .connectorRef(asgInfrastructure.getConnectorRef().getValue())
                .environment(environmentOutcome)
                .region(asgInfrastructure.getRegion().getValue())
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, asgInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(
            asgInfrastructureOutcome, asgInfrastructure.getInfraIdentifier(), asgInfrastructure.getInfraName());
        infrastructureOutcome = asgInfrastructureOutcome;
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        CustomDeploymentInfrastructure customDeploymentInfrastructure = (CustomDeploymentInfrastructure) infrastructure;
        String templateYaml = customDeploymentInfrastructureHelper.getTemplateYaml(accountIdentifier, orgIdentifier,
            projectIdentifier, customDeploymentInfrastructure.getCustomDeploymentRef().getTemplateRef(),
            customDeploymentInfrastructure.getCustomDeploymentRef().getVersionLabel());
        List<String> infraKeys =
            new ArrayList<>(Arrays.asList(customDeploymentInfrastructure.getInfrastructureKeyValues()));
        infraKeys.add(customDeploymentInfrastructure.getInfraIdentifier());
        CustomDeploymentInfrastructureOutcome customDeploymentInfrastructureOutcome =
            CustomDeploymentInfrastructureOutcome.builder()
                .variables(customDeploymentInfrastructureHelper.convertListVariablesToMap(
                    customDeploymentInfrastructure.getVariables(), accountIdentifier, orgIdentifier, projectIdentifier))
                .instanceAttributes(
                    customDeploymentInfrastructureHelper.getInstanceAttributes(templateYaml, accountIdentifier))
                .instanceFetchScript(customDeploymentInfrastructureHelper.getScript(
                    templateYaml, accountIdentifier, orgIdentifier, projectIdentifier))
                .instancesListPath(
                    customDeploymentInfrastructureHelper.getInstancePath(templateYaml, accountIdentifier))
                .environment(environmentOutcome)
                .infrastructureKey(
                    InfrastructureKey.generate(service, environmentOutcome, infraKeys.toArray(new String[0])))
                .build();
        setInfraIdentifierAndName(customDeploymentInfrastructureOutcome,
            customDeploymentInfrastructure.getInfraIdentifier(), customDeploymentInfrastructure.getInfraName());
        infrastructureOutcome = customDeploymentInfrastructureOutcome;
        break;

      case InfrastructureKind.TAS:
        TanzuApplicationServiceInfrastructure tanzuInfrastructure =
            (TanzuApplicationServiceInfrastructure) infrastructure;

        TanzuApplicationServiceInfrastructureOutcome tanzuInfrastructureOutcome =
            TanzuApplicationServiceInfrastructureOutcome.builder()
                .connectorRef(tanzuInfrastructure.getConnectorRef().getValue())
                .organization(tanzuInfrastructure.getOrganization().getValue())
                .space(tanzuInfrastructure.getSpace().getValue())
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, tanzuInfrastructure.getInfrastructureKeyValues()))
                .build();

        setInfraIdentifierAndName(
            tanzuInfrastructureOutcome, tanzuInfrastructure.getInfraIdentifier(), tanzuInfrastructure.getInfraName());
        infrastructureOutcome = tanzuInfrastructureOutcome;
        break;
      case InfrastructureKind.AWS_SAM:
        AwsSamInfrastructure awsSamInfrastructure = (AwsSamInfrastructure) infrastructure;
        AwsSamInfrastructureOutcome awsSamInfrastructureOutcome =
            AwsSamInfrastructureOutcome.builder()
                .connectorRef(awsSamInfrastructure.getConnectorRef().getValue())
                .environment(environmentOutcome)
                .region(awsSamInfrastructure.getRegion().getValue())
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, awsSamInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(awsSamInfrastructureOutcome, awsSamInfrastructure.getInfraIdentifier(),
            awsSamInfrastructure.getInfraName());
        infrastructureOutcome = awsSamInfrastructureOutcome;
        break;

      case InfrastructureKind.AWS_LAMBDA:
        AwsLambdaInfrastructure awsLambdaInfrastructure = (AwsLambdaInfrastructure) infrastructure;
        AwsLambdaInfrastructureOutcome awsLambdaInfrastructureOutcome =
            AwsLambdaInfrastructureOutcome.builder()
                .connectorRef(awsLambdaInfrastructure.getConnectorRef().getValue())
                .region(awsLambdaInfrastructure.getRegion().getValue())
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, awsLambdaInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(awsLambdaInfrastructureOutcome, awsLambdaInfrastructure.getInfraIdentifier(),
            awsLambdaInfrastructure.getInfraName());
        infrastructureOutcome = awsLambdaInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        K8sAwsInfrastructureOutcome k8sAwsInfrastructureOutcome =
            K8sAwsInfrastructureOutcome.builder()
                .connectorRef(k8sAwsInfrastructure.getConnectorRef().getValue())
                .namespace(k8sAwsInfrastructure.getNamespace().getValue())
                .cluster(k8sAwsInfrastructure.getCluster().getValue())
                .releaseName(getValueOrExpression(k8sAwsInfrastructure.getReleaseName()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, k8sAwsInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(k8sAwsInfrastructureOutcome, k8sAwsInfrastructure.getInfraIdentifier(),
            k8sAwsInfrastructure.getInfraName());
        infrastructureOutcome = k8sAwsInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        K8sRancherInfrastructureOutcome rancherInfrastructureOutcome =
            K8sRancherInfrastructureOutcome.builder()
                .connectorRef(rancherInfrastructure.getConnectorRef().getValue())
                .namespace(rancherInfrastructure.getNamespace().getValue())
                .clusterName(rancherInfrastructure.getCluster().getValue())
                .releaseName(getValueOrExpression(rancherInfrastructure.getReleaseName()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, rancherInfrastructure.getInfrastructureKeyValues()))
                .build();

        setInfraIdentifierAndName(rancherInfrastructureOutcome, rancherInfrastructure.getInfraIdentifier(),
            rancherInfrastructure.getInfraName());
        infrastructureOutcome = rancherInfrastructureOutcome;
        break;

      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }

    setConnectorInOutcome(infrastructure, accountIdentifier, projectIdentifier, orgIdentifier, infrastructureOutcome);

    return infrastructureOutcome;
  }

  private void setConnectorInOutcome(Infrastructure infrastructure, String accountIdentifier, String projectIdentifier,
      String orgIdentifier, InfrastructureOutcomeAbstract infrastructureOutcome) {
    if (ParameterField.isNotNull(infrastructure.getConnectorReference())
        && !infrastructure.getConnectorReference().isExpression()) {
      Optional<ConnectorResponseDTO> connector = connectorService.getByRef(
          accountIdentifier, orgIdentifier, projectIdentifier, infrastructure.getConnectorReference().getValue());

      connector.ifPresent(c
          -> infrastructureOutcome.setConnector(
              Connector.builder().name(c.getConnector() != null ? c.getConnector().getName() : "").build()));
    }
  }

  private void setPdcInfrastructureHostValueSplittingStringToListIfNeeded(PdcInfrastructure pdcInfrastructure) {
    if (pdcInfrastructure.getHosts() == null) {
      return;
    }

    pdcInfrastructure.getHosts().setValue(
        ParameterFieldHelper.getParameterFieldListValueBySeparator(pdcInfrastructure.getHosts(), HOSTS_SEPARATOR));
  }

  private HostFilterDTO toHostFilterDTO(HostFilter hostFilter) {
    if (hostFilter == null) {
      return HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(HostFilterType.ALL).build();
    }

    HostFilterType type = hostFilter.getType();
    HostFilterSpec spec = hostFilter.getSpec();
    if (type == HostFilterType.HOST_NAMES) {
      return HostFilterDTO.builder()
          .spec(HostNamesFilterDTO.builder().value(((HostNamesFilter) spec).getValue()).build())
          .type(type)
          .build();
    } else if (type == HostFilterType.HOST_ATTRIBUTES) {
      return HostFilterDTO.builder()
          .spec(HostAttributesFilterDTO.builder().value(((HostAttributesFilter) spec).getValue()).build())
          .type(type)
          .build();
    } else if (type == HostFilterType.ALL) {
      return HostFilterDTO.builder().spec(AllHostsFilterDTO.builder().build()).type(type).build();
    } else {
      throw new InvalidArgumentsException(format("Unsupported host filter type found: %s", type));
    }
  }

  public void setInfraIdentifierAndName(
      InfrastructureOutcomeAbstract infrastructureOutcome, String infraIdentifier, String infraName) {
    infrastructureOutcome.setInfraIdentifier(infraIdentifier);
    infrastructureOutcome.setInfraName(infraName);
    infrastructureOutcome.setName(infraName);
  }

  private String getValueOrExpression(ParameterField<String> parameterField) {
    if (parameterField.isExpression()) {
      return parameterField.getExpressionValue();
    } else {
      return parameterField.getValue();
    }
  }
}
