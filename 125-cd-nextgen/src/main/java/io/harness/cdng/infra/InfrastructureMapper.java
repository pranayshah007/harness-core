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
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.cdng.infra.beans.EcsInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcomeAbstract;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAwsInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.host.HostAttributesFilter;
import io.harness.cdng.infra.beans.host.HostFilter;
import io.harness.cdng.infra.beans.host.HostFilterSpec;
import io.harness.cdng.infra.beans.host.HostNamesFilter;
import io.harness.cdng.infra.beans.host.dto.AllHostsFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostAttributesFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostFilterDTO;
import io.harness.cdng.infra.beans.host.dto.HostNamesFilterDTO;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
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
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
public class InfrastructureMapper {
  @Inject private CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @NotNull
  public InfrastructureOutcome toOutcome(@Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome,
      ServiceStepOutcome service, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    final InfrastructureOutcomeAbstract infrastructureOutcome;
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        validateK8sDirectInfrastructure(k8SDirectInfrastructure);
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
        validateK8sGcpInfrastructure(k8sGcpInfrastructure);
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
        validateServerlessAwsInfrastructure(serverlessAwsLambdaInfrastructure);
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
        validateK8sAzureInfrastructure(k8sAzureInfrastructure);
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
        validatePdcInfrastructure(pdcInfrastructure);
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
        validateSshWinRmAwsInfrastructure(sshWinRmAwsInfrastructure);

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
        validateSshWinRmAzureInfrastructure(sshWinRmAzureInfrastructure);
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
        validateAzureWebAppInfrastructure(azureWebAppInfrastructure);
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
        validateEcsInfrastructure(ecsInfrastructure);
        EcsInfrastructureOutcome ecsInfrastructureOutcome =
            EcsInfrastructureOutcome.builder()
                .connectorRef(ecsInfrastructure.getConnectorRef().getValue())
                .region(ecsInfrastructure.getRegion().getValue())
                .cluster(ecsInfrastructure.getCluster().getValue())
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, ecsInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(
            ecsInfrastructureOutcome, ecsInfrastructure.getInfraIdentifier(), ecsInfrastructure.getInfraName());
        infrastructureOutcome = ecsInfrastructureOutcome;
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        CustomDeploymentInfrastructure customDeploymentInfrastructure = (CustomDeploymentInfrastructure) infrastructure;
        String templateYaml = customDeploymentInfrastructureHelper.getTemplateYaml(accountIdentifier, orgIdentifier,
            projectIdentifier, customDeploymentInfrastructure.getCustomDeploymentRef().getTemplateRef(),
            customDeploymentInfrastructure.getCustomDeploymentRef().getVersionLabel());
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
                .infrastructureKey(InfrastructureKey.generate(
                    service, environmentOutcome, customDeploymentInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(customDeploymentInfrastructureOutcome,
            customDeploymentInfrastructure.getInfraIdentifier(), customDeploymentInfrastructure.getInfraName());
        infrastructureOutcome = customDeploymentInfrastructureOutcome;
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
  }

  private void validateK8sDirectInfrastructure(K8SDirectInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of("namespace", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "cannot be empty"));
    }
  }

  private void validateK8sGcpInfrastructure(K8sGcpInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of("namespace", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of("cluster", "cannot be empty"));
    }
  }

  private void validateK8sAzureInfrastructure(K8sAzureInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of("namespace", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of("cluster", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getSubscriptionId())
        || isEmpty(getParameterFieldValue(infrastructure.getSubscriptionId()))) {
      throw new InvalidArgumentsException(Pair.of("subscription", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getResourceGroup())
        || isEmpty(getParameterFieldValue(infrastructure.getResourceGroup()))) {
      throw new InvalidArgumentsException(Pair.of("resourceGroup", "cannot be empty"));
    }
  }

  private void validateAzureWebAppInfrastructure(AzureWebAppInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getConnectorRef())
        || isEmpty(getParameterFieldValue(infrastructure.getConnectorRef()))) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getSubscriptionId())
        || isEmpty(getParameterFieldValue(infrastructure.getSubscriptionId()))) {
      throw new InvalidArgumentsException(Pair.of("subscription", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getResourceGroup())
        || isEmpty(getParameterFieldValue(infrastructure.getResourceGroup()))) {
      throw new InvalidArgumentsException(Pair.of("resourceGroup", "cannot be empty"));
    }
  }

  private void validatePdcInfrastructure(PdcInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", "cannot be empty"));
    }

    if (!hasValueListOrExpression(infrastructure.getHosts())
        && !hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("hosts", "cannot be empty"),
          Pair.of("connectorRef", "cannot be empty"),
          new IllegalArgumentException("hosts and connectorRef are not defined"));
    }
  }

  private void validateServerlessAwsInfrastructure(ServerlessAwsLambdaInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getRegion())
        || isEmpty(getParameterFieldValue(infrastructure.getRegion()))) {
      throw new InvalidArgumentsException(Pair.of("region", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getStage())) {
      throw new InvalidArgumentsException(Pair.of("stage", "cannot be empty"));
    }
  }

  private static void validateSshWinRmAzureInfrastructure(SshWinRmAzureInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getSubscriptionId())) {
      throw new InvalidArgumentsException(Pair.of("subscriptionId", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getResourceGroup())) {
      throw new InvalidArgumentsException(Pair.of("resourceGroup", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", "cannot be empty"));
    }
  }

  private void validateSshWinRmAwsInfrastructure(SshWinRmAwsInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of("region", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getHostConnectionType())) {
      throw new InvalidArgumentsException(Pair.of("hostConnectionType", "cannot be empty"));
    }

    if (infrastructure.getAwsInstanceFilter() == null) {
      throw new InvalidArgumentsException(Pair.of("awsInstanceFilter", "cannot be null"));
    }
  }

  private static void validateEcsInfrastructure(EcsInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getCluster())) {
      throw new InvalidArgumentsException(Pair.of("cluster", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of("region", "cannot be empty"));
    }
  }

  private static boolean hasValueOrExpression(ParameterField<String> parameterField) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    return parameterField.isExpression() || !isEmpty(getParameterFieldValue(parameterField));
  }

  private <T> boolean hasValueListOrExpression(ParameterField<List<T>> parameterField) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    return parameterField.isExpression() || !isEmpty(getParameterFieldValue(parameterField));
  }

  private String getValueOrExpression(ParameterField<String> parameterField) {
    if (parameterField.isExpression()) {
      return parameterField.getExpressionValue();
    } else {
      return parameterField.getValue();
    }
  }
}
