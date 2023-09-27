/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.beans.FeatureName.CDS_ENABLE_NEW_PARAMETER_FIELD_PROCESSOR;
import static io.harness.cdng.infra.beans.host.dto.HostFilterSpecDTO.HOSTS_SEPARATOR;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.InfrastructureKeyGenerator.InfraKey;
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
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.pdcconnector.HostFilterType;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.evaluators.ProvisionerExpressionEvaluatorProvider;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDP)
public class InfrastructureMapper {
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private PdcProvisionedInfrastructureMapper pdcProvisionedInfrastructureMapper;
  @Inject private ProvisionerExpressionEvaluatorProvider provisionerExpressionEvaluatorProvider;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  public InfrastructureOutcome toOutcome(@Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome,
      ServiceStepOutcome service, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Map<String, String> tags) {
    return toOutcome(
        infrastructure, null, environmentOutcome, service, accountIdentifier, orgIdentifier, projectIdentifier, tags);
  }

  @NotNull
  public InfrastructureOutcome toOutcome(@Nonnull Infrastructure infrastructure, Ambiance ambiance,
      EnvironmentOutcome environmentOutcome, ServiceStepOutcome service, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, String> tags) {
    Map<String, String> mergedTags = new HashMap<>();
    Map<String, String> hostTags;

    final boolean isDynamicallyProvisioned = infrastructure.isDynamicallyProvisioned();

    ProvisionerExpressionEvaluator expressionEvaluator = (ambiance != null)
        ? provisionerExpressionEvaluatorProvider.getProvisionerExpressionEvaluator(
            ambiance, infrastructure.getProvisionerStepIdentifier())
        : new ProvisionerExpressionEvaluator(Collections.emptyMap(), inputSetValidatorFactory);

    if (EmptyPredicate.isNotEmpty(tags)) {
      mergedTags.putAll(tags);
    }

    final InfrastructureOutcomeAbstract infrastructureOutcome;

    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        InfraKey k8sDirectInfraKey = InfrastructureKeyGenerator.createInfraKey(
            service, environmentOutcome, k8SDirectInfrastructure.getInfrastructureKeyValues());
        Optional<String> k8sDirectServiceReleaseName = getReleaseName(service);
        String k8sDirectReleaseName =
            k8sDirectServiceReleaseName.orElseGet(() -> getValueOrExpression(k8SDirectInfrastructure.getReleaseName()));
        K8sDirectInfrastructureOutcome k8SDirectInfrastructureOutcome =
            K8sDirectInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(k8SDirectInfrastructure.getConnectorRef()))
                .namespace(getParameterFieldValue(k8SDirectInfrastructure.getNamespace()))
                .releaseName(k8sDirectReleaseName)
                .environment(environmentOutcome)
                .infrastructureKey(k8sDirectInfraKey.getKey())
                .infrastructureKeyShort(k8sDirectInfraKey.getShortKey())
                .build();
        setInfraIdentifierAndName(k8SDirectInfrastructureOutcome, k8SDirectInfrastructure.getInfraIdentifier(),
            k8SDirectInfrastructure.getInfraName());
        infrastructureOutcome = k8SDirectInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        InfraKey k8sGcpInfraKey = InfrastructureKeyGenerator.createInfraKey(
            service, environmentOutcome, k8sGcpInfrastructure.getInfrastructureKeyValues());
        Optional<String> k8sGcpServiceReleaseName = getReleaseName(service);
        String k8sGcpReleaseName =
            k8sGcpServiceReleaseName.orElseGet(() -> getValueOrExpression(k8sGcpInfrastructure.getReleaseName()));
        K8sGcpInfrastructureOutcome k8sGcpInfrastructureOutcome =
            K8sGcpInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(k8sGcpInfrastructure.getConnectorRef()))
                .namespace(getParameterFieldValue(k8sGcpInfrastructure.getNamespace()))
                .cluster(getParameterFieldValue(k8sGcpInfrastructure.getCluster()))
                .releaseName(k8sGcpReleaseName)
                .environment(environmentOutcome)
                .infrastructureKey(k8sGcpInfraKey.getKey())
                .infrastructureKeyShort(k8sGcpInfraKey.getShortKey())
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
                .connectorRef(getParameterFieldValue(serverlessAwsLambdaInfrastructure.getConnectorRef()))
                .region(getParameterFieldValue(serverlessAwsLambdaInfrastructure.getRegion()))
                .stage(getParameterFieldValue(serverlessAwsLambdaInfrastructure.getStage()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, serverlessAwsLambdaInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(serverlessAwsLambdaInfrastructureOutcome,
            serverlessAwsLambdaInfrastructure.getInfraIdentifier(), serverlessAwsLambdaInfrastructure.getInfraName());
        infrastructureOutcome = serverlessAwsLambdaInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        InfraKey k8sAzureInfraKey = InfrastructureKeyGenerator.createInfraKey(
            service, environmentOutcome, k8sAzureInfrastructure.getInfrastructureKeyValues());
        Optional<String> k8sAzureServiceReleaseName = getReleaseName(service);
        String k8sAzureReleaseName =
            k8sAzureServiceReleaseName.orElseGet(() -> getValueOrExpression(k8sAzureInfrastructure.getReleaseName()));
        K8sAzureInfrastructureOutcome k8sAzureInfrastructureOutcome =
            K8sAzureInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(k8sAzureInfrastructure.getConnectorRef()))
                .namespace(getParameterFieldValue(k8sAzureInfrastructure.getNamespace()))
                .cluster(getParameterFieldValue(k8sAzureInfrastructure.getCluster()))
                .releaseName(k8sAzureReleaseName)
                .environment(environmentOutcome)
                .infrastructureKey(k8sAzureInfraKey.getKey())
                .infrastructureKeyShort(k8sAzureInfraKey.getShortKey())
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
        if (isDynamicallyProvisioned) {
          infrastructureOutcome =
              pdcProvisionedInfrastructureMapper.toOutcome(pdcInfrastructure, ambiance, environmentOutcome, service);
          break;
        }
        setPdcInfrastructureHostValueSplittingStringToListIfNeeded(pdcInfrastructure);
        PdcInfrastructureOutcome pdcInfrastructureOutcome =
            PdcInfrastructureOutcome.builder()
                .credentialsRef(getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
                .hosts(getParameterFieldValue(pdcInfrastructure.getHosts()))
                .connectorRef(getParameterFieldValue(pdcInfrastructure.getConnectorRef()))
                .hostFilter(toHostFilterDTO(pdcInfrastructure.getHostFilter()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, pdcInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(
            pdcInfrastructureOutcome, pdcInfrastructure.getInfraIdentifier(), pdcInfrastructure.getInfraName());
        infrastructureOutcome = pdcInfrastructureOutcome;
        break;

      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        if (sshWinRmAwsInfrastructure.getAwsInstanceFilter() != null) {
          hostTags = getHostTags(
              sshWinRmAwsInfrastructure.getAwsInstanceFilter().getTags(), accountIdentifier, expressionEvaluator);
        } else {
          hostTags = null;
        }
        SshWinRmAwsInfrastructureOutcome sshWinRmAwsInfrastructureOutcome =
            SshWinRmAwsInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(sshWinRmAwsInfrastructure.getConnectorRef()))
                .credentialsRef(getParameterFieldValue(sshWinRmAwsInfrastructure.getCredentialsRef()))
                .region(getParameterFieldValue(sshWinRmAwsInfrastructure.getRegion()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, infrastructure.getInfrastructureKeyValues()))
                .hostTags(hostTags)
                .hostConnectionType(getParameterFieldValue(sshWinRmAwsInfrastructure.getHostConnectionType()))
                .build();

        setInfraIdentifierAndName(sshWinRmAwsInfrastructureOutcome, sshWinRmAwsInfrastructure.getInfraIdentifier(),
            sshWinRmAwsInfrastructure.getInfraName());
        if (EmptyPredicate.isNotEmpty(hostTags)) {
          mergedTags.putAll(hostTags);
        }
        infrastructureOutcome = sshWinRmAwsInfrastructureOutcome;
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        hostTags = getHostTags(sshWinRmAzureInfrastructure.getTags(), accountIdentifier, expressionEvaluator);
        SshWinRmAzureInfrastructureOutcome sshWinRmAzureInfrastructureOutcome =
            SshWinRmAzureInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(sshWinRmAzureInfrastructure.getConnectorRef()))
                .subscriptionId(getParameterFieldValue(sshWinRmAzureInfrastructure.getSubscriptionId()))
                .resourceGroup(getParameterFieldValue(sshWinRmAzureInfrastructure.getResourceGroup()))
                .credentialsRef(getParameterFieldValue(sshWinRmAzureInfrastructure.getCredentialsRef()))
                .hostTags(hostTags)
                .hostConnectionType(getParameterFieldValue(sshWinRmAzureInfrastructure.getHostConnectionType()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, sshWinRmAzureInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(sshWinRmAzureInfrastructureOutcome, sshWinRmAzureInfrastructure.getInfraIdentifier(),
            sshWinRmAzureInfrastructure.getInfraName());
        if (EmptyPredicate.isNotEmpty(hostTags)) {
          mergedTags.putAll(hostTags);
        }
        infrastructureOutcome = sshWinRmAzureInfrastructureOutcome;
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        AzureWebAppInfrastructureOutcome azureWebAppInfrastructureOutcome =
            AzureWebAppInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(azureWebAppInfrastructure.getConnectorRef()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, azureWebAppInfrastructure.getInfrastructureKeyValues()))
                .subscription(getParameterFieldValue(azureWebAppInfrastructure.getSubscriptionId()))
                .resourceGroup(getParameterFieldValue(azureWebAppInfrastructure.getResourceGroup()))
                .build();
        setInfraIdentifierAndName(azureWebAppInfrastructureOutcome, azureWebAppInfrastructure.getInfraIdentifier(),
            azureWebAppInfrastructure.getInfraName());
        infrastructureOutcome = azureWebAppInfrastructureOutcome;
        break;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        EcsInfrastructureOutcome ecsInfrastructureOutcome =
            EcsInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(ecsInfrastructure.getConnectorRef()))
                .environment(environmentOutcome)
                .region(getParameterFieldValue(ecsInfrastructure.getRegion()))
                .cluster(getParameterFieldValue(ecsInfrastructure.getCluster()))
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
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
                .connectorRef(getParameterFieldValue(googleFunctionsInfrastructure.getConnectorRef()))
                .environment(environmentOutcome)
                .region(getParameterFieldValue(googleFunctionsInfrastructure.getRegion()))
                .project(getParameterFieldValue(googleFunctionsInfrastructure.getProject()))
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
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
                .connectorRef(getParameterFieldValue(elastigroupInfrastructure.getConnectorRef()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
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
                .connectorRef(getParameterFieldValue(asgInfrastructure.getConnectorRef()))
                .environment(environmentOutcome)
                .region(getParameterFieldValue(asgInfrastructure.getRegion()))
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, asgInfrastructure.getInfrastructureKeyValues()))
                .baseAsgName(
                    asgInfrastructure.getBaseAsgName() != null ? asgInfrastructure.getBaseAsgName().getValue() : null)
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
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, infraKeys.toArray(new String[0])))
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
                .connectorRef(getParameterFieldValue(tanzuInfrastructure.getConnectorRef()))
                .organization(getParameterFieldValue(tanzuInfrastructure.getOrganization()))
                .space(getParameterFieldValue(tanzuInfrastructure.getSpace()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
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
                .connectorRef(getParameterFieldValue(awsSamInfrastructure.getConnectorRef()))
                .environment(environmentOutcome)
                .region(getParameterFieldValue(awsSamInfrastructure.getRegion()))
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
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
                .connectorRef(getParameterFieldValue(awsLambdaInfrastructure.getConnectorRef()))
                .region(getParameterFieldValue(awsLambdaInfrastructure.getRegion()))
                .environment(environmentOutcome)
                .infrastructureKey(InfrastructureKeyGenerator.createFullInfraKey(
                    service, environmentOutcome, awsLambdaInfrastructure.getInfrastructureKeyValues()))
                .build();
        setInfraIdentifierAndName(awsLambdaInfrastructureOutcome, awsLambdaInfrastructure.getInfraIdentifier(),
            awsLambdaInfrastructure.getInfraName());
        infrastructureOutcome = awsLambdaInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        InfraKey k8sAwsInfraKey = InfrastructureKeyGenerator.createInfraKey(
            service, environmentOutcome, k8sAwsInfrastructure.getInfrastructureKeyValues());
        Optional<String> k8sAwsServiceReleaseName = getReleaseName(service);
        String k8sAwsReleaseName =
            k8sAwsServiceReleaseName.orElseGet(() -> getValueOrExpression(k8sAwsInfrastructure.getReleaseName()));
        K8sAwsInfrastructureOutcome k8sAwsInfrastructureOutcome =
            K8sAwsInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(k8sAwsInfrastructure.getConnectorRef()))
                .namespace(getParameterFieldValue(k8sAwsInfrastructure.getNamespace()))
                .cluster(getParameterFieldValue(k8sAwsInfrastructure.getCluster()))
                .releaseName(k8sAwsReleaseName)
                .environment(environmentOutcome)
                .infrastructureKey(k8sAwsInfraKey.getKey())
                .infrastructureKeyShort(k8sAwsInfraKey.getShortKey())
                .build();
        setInfraIdentifierAndName(k8sAwsInfrastructureOutcome, k8sAwsInfrastructure.getInfraIdentifier(),
            k8sAwsInfrastructure.getInfraName());
        infrastructureOutcome = k8sAwsInfrastructureOutcome;
        break;

      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        InfraKey k8sRancherInfraKey = InfrastructureKeyGenerator.createInfraKey(
            service, environmentOutcome, rancherInfrastructure.getInfrastructureKeyValues());
        Optional<String> k8sRancherServiceReleaseName = getReleaseName(service);
        String k8sRancherReleaseName =
            k8sRancherServiceReleaseName.orElseGet(() -> getValueOrExpression(rancherInfrastructure.getReleaseName()));
        K8sRancherInfrastructureOutcome rancherInfrastructureOutcome =
            K8sRancherInfrastructureOutcome.builder()
                .connectorRef(getParameterFieldValue(rancherInfrastructure.getConnectorRef()))
                .namespace(getParameterFieldValue(rancherInfrastructure.getNamespace()))
                .clusterName(getParameterFieldValue(rancherInfrastructure.getCluster()))
                .releaseName(k8sRancherReleaseName)
                .environment(environmentOutcome)
                .infrastructureKey(k8sRancherInfraKey.getKey())
                .infrastructureKeyShort(k8sRancherInfraKey.getShortKey())
                .build();

        setInfraIdentifierAndName(rancherInfrastructureOutcome, rancherInfrastructure.getInfraIdentifier(),
            rancherInfrastructure.getInfraName());
        infrastructureOutcome = rancherInfrastructureOutcome;
        break;

      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }

    setConnectorInOutcome(infrastructure, accountIdentifier, projectIdentifier, orgIdentifier, infrastructureOutcome);

    if (EmptyPredicate.isNotEmpty(mergedTags)) {
      infrastructureOutcome.setTags(mergedTags);
    }
    return infrastructureOutcome;
  }

  private Optional<String> getReleaseName(ServiceStepOutcome service) {
    if (service != null && service.getRelease() != null && service.getRelease().getName() != null) {
      return Optional.of(service.getRelease().getName());
    }

    return Optional.empty();
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

  private Map<String, String> getHostTags(ParameterField<Map<String, String>> tags, String accountIdentifier,
      ProvisionerExpressionEvaluator expressionEvaluator) {
    // TODO: Remove this If condition when the new expression resolution FF is GAed
    if (!ngFeatureFlagHelperService.isEnabled(accountIdentifier, CDS_ENABLE_NEW_PARAMETER_FIELD_PROCESSOR)) {
      return expressionEvaluator.evaluateExpression(tags, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    } else {
      return getParameterFieldValue(tags);
    }
  }
}
