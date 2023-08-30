/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.cdng.ssh.SshWinRmConstants.HOSTNAME_HOST_ATTRIBUTE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.hasValueListOrExpression;
import static io.harness.common.ParameterFieldHelper.hasValueOrExpression;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.logging.LogCallbackUtils.saveExecutionLogSafely;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.CustomDeploymentInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.ElastigroupInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureDetailsAbstract;
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
import io.harness.data.structure.EmptyPredicate;
import io.harness.evaluators.ProviderExpressionEvaluatorProvider;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.common.ExpressionMode;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class InfrastructureValidator {
  private static final String CANNOT_BE_EMPTY_ERROR_MSG = "cannot be empty";
  private static final String NOT_PROVIDED_ERROR_MSG = " set as runtime input but no value was provided";
  private static final String AWS_REGION = "region";
  private static final String INPUT_EXPRESSION = "<+input>";
  private static final String K8S_NAMESPACE = "namespace";
  private static final String K8S_RELEASE_NAME = "releaseName";
  private static final String K8S_CLUSTER_NAME = "cluster";
  private static final String SUBSCRIPTION = "subscription";
  private static final String RESOURCE_GROUP = "resourceGroup";
  @Inject private CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  @Inject private ProviderExpressionEvaluatorProvider providerExpressionEvaluatorProvider;

  @VisibleForTesting
  public void validateInfrastructure(Infrastructure infrastructure, Ambiance ambiance, NGLogCallback logCallback) {
    String k8sNamespaceLogLine = "Kubernetes Namespace: %s";
    if (infrastructure == null) {
      throw new InvalidRequestException("Infrastructure definition can't be null or empty");
    }

    if (infrastructure instanceof InfrastructureDetailsAbstract) {
      saveExecutionLogSafely(logCallback,
          "Infrastructure Name: " + ((InfrastructureDetailsAbstract) infrastructure).getInfraName()
              + " , Identifier: " + ((InfrastructureDetailsAbstract) infrastructure).getInfraIdentifier());
    }

    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        validateExpression(k8SDirectInfrastructure.getConnectorRef(), k8SDirectInfrastructure.getNamespace());

        if (k8SDirectInfrastructure.getNamespace() != null
            && isNotEmpty(k8SDirectInfrastructure.getNamespace().getValue())) {
          saveExecutionLogSafely(logCallback,
              color(format(k8sNamespaceLogLine, k8SDirectInfrastructure.getNamespace().getValue()), Yellow));
        }
        validateK8sDirectInfrastructure(k8SDirectInfrastructure);
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        CustomDeploymentInfrastructure customDeploymentInfrastructure = (CustomDeploymentInfrastructure) infrastructure;
        validateExpression(customDeploymentInfrastructure.getConnectorReference(),
            ParameterField.createValueField(customDeploymentInfrastructure.getCustomDeploymentRef().getTemplateRef()),
            ParameterField.createValueField(customDeploymentInfrastructure.getCustomDeploymentRef().getVersionLabel()));
        customDeploymentInfrastructureHelper.validateInfra(ambiance, customDeploymentInfrastructure);
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        validateExpression(k8sGcpInfrastructure.getConnectorRef(), k8sGcpInfrastructure.getNamespace(),
            k8sGcpInfrastructure.getCluster());

        if (k8sGcpInfrastructure.getNamespace() != null && isNotEmpty(k8sGcpInfrastructure.getNamespace().getValue())) {
          saveExecutionLogSafely(
              logCallback, color(format(k8sNamespaceLogLine, k8sGcpInfrastructure.getNamespace().getValue()), Yellow));
        }
        validateK8sGcpInfrastructure(k8sGcpInfrastructure);
        break;
      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        validateExpression(serverlessAwsLambdaInfrastructure.getConnectorRef(),
            serverlessAwsLambdaInfrastructure.getRegion(), serverlessAwsLambdaInfrastructure.getStage());
        validateServerlessAwsInfrastructure((ServerlessAwsLambdaInfrastructure) infrastructure);
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        validateExpression(k8sAzureInfrastructure.getConnectorRef(), k8sAzureInfrastructure.getNamespace(),
            k8sAzureInfrastructure.getCluster(), k8sAzureInfrastructure.getSubscriptionId(),
            k8sAzureInfrastructure.getResourceGroup());

        if (k8sAzureInfrastructure.getNamespace() != null
            && isNotEmpty(k8sAzureInfrastructure.getNamespace().getValue())) {
          saveExecutionLogSafely(logCallback,
              color(format(k8sNamespaceLogLine, k8sAzureInfrastructure.getNamespace().getValue()), Yellow));
        }
        validateK8sAzureInfrastructure(k8sAzureInfrastructure);
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        validateExpression(sshWinRmAzureInfrastructure.getConnectorRef(),
            sshWinRmAzureInfrastructure.getSubscriptionId(), sshWinRmAzureInfrastructure.getResourceGroup(),
            sshWinRmAzureInfrastructure.getCredentialsRef());
        validateSshWinRmAzureInfrastructure((SshWinRmAzureInfrastructure) infrastructure);
        break;

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
        validateExpression(pdcInfrastructure.getCredentialsRef());
        requireOne(pdcInfrastructure.getHosts(), pdcInfrastructure.getConnectorRef());
        validatePdcInfrastructure((PdcInfrastructure) infrastructure);
        break;
      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        validateExpression(sshWinRmAwsInfrastructure.getConnectorRef(), sshWinRmAwsInfrastructure.getCredentialsRef(),
            sshWinRmAwsInfrastructure.getRegion(), sshWinRmAwsInfrastructure.getHostConnectionType());
        validateSshWinRmAwsInfrastructure((SshWinRmAwsInfrastructure) infrastructure);
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        validateExpression(azureWebAppInfrastructure.getConnectorRef(), azureWebAppInfrastructure.getSubscriptionId(),
            azureWebAppInfrastructure.getResourceGroup());
        validateAzureWebAppInfrastructure((AzureWebAppInfrastructure) infrastructure);
        break;

      case InfrastructureKind.ELASTIGROUP:
        ElastigroupInfrastructure elastigroupInfrastructure = (ElastigroupInfrastructure) infrastructure;
        validateExpression(elastigroupInfrastructure.getConnectorRef());
        validateElastigroupInfrastructure((ElastigroupInfrastructure) infrastructure);
        break;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        validateExpression(
            ecsInfrastructure.getConnectorRef(), ecsInfrastructure.getCluster(), ecsInfrastructure.getRegion());
        validateEcsInfrastructure((EcsInfrastructure) infrastructure);
        break;

      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        GoogleFunctionsInfrastructure googleFunctionsInfrastructure = (GoogleFunctionsInfrastructure) infrastructure;
        validateExpression(googleFunctionsInfrastructure.getConnectorRef(), googleFunctionsInfrastructure.getProject(),
            googleFunctionsInfrastructure.getRegion());
        validateGoogleFunctionsInfrastructure((GoogleFunctionsInfrastructure) infrastructure);
        break;

      case InfrastructureKind.TAS:
        TanzuApplicationServiceInfrastructure tanzuApplicationServiceInfrastructure =
            (TanzuApplicationServiceInfrastructure) infrastructure;
        validateExpression(tanzuApplicationServiceInfrastructure.getConnectorRef(),
            tanzuApplicationServiceInfrastructure.getOrganization(), tanzuApplicationServiceInfrastructure.getSpace());
        validateTanzuApplicationServiceInfrastructure((TanzuApplicationServiceInfrastructure) infrastructure);
        break;

      case InfrastructureKind.ASG:
        AsgInfrastructure asgInfrastructure = (AsgInfrastructure) infrastructure;
        validateExpression(asgInfrastructure.getConnectorRef(), asgInfrastructure.getRegion());
        validateAsgInfrastructure((AsgInfrastructure) infrastructure);
        break;

      case InfrastructureKind.AWS_SAM:
        AwsSamInfrastructure awsSamInfrastructure = (AwsSamInfrastructure) infrastructure;
        validateExpression(awsSamInfrastructure.getConnectorRef(), awsSamInfrastructure.getRegion());
        validateAwsSamInfrastructure((AwsSamInfrastructure) infrastructure);
        break;

      case InfrastructureKind.AWS_LAMBDA:
        AwsLambdaInfrastructure awsLambdaInfrastructure = (AwsLambdaInfrastructure) infrastructure;
        validateExpression(awsLambdaInfrastructure.getConnectorRef(), awsLambdaInfrastructure.getRegion());
        validateAwsLambdaInfrastructure((AwsLambdaInfrastructure) infrastructure);
        break;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        validateExpression(k8sAwsInfrastructure.getConnectorRef(), k8sAwsInfrastructure.getNamespace(),
            k8sAwsInfrastructure.getCluster());

        if (k8sAwsInfrastructure.getNamespace() != null && isNotEmpty(k8sAwsInfrastructure.getNamespace().getValue())) {
          saveExecutionLogSafely(
              logCallback, color(format(k8sNamespaceLogLine, k8sAwsInfrastructure.getNamespace().getValue()), Yellow));
        }
        validateK8sAwsInfrastructure(k8sAwsInfrastructure);
        break;

      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        validateExpression(rancherInfrastructure.getConnectorRef(), rancherInfrastructure.getNamespace(),
            rancherInfrastructure.getCluster());
        if (ParameterField.isNotNull(rancherInfrastructure.getNamespace())
            && isNotEmpty(rancherInfrastructure.getNamespace().getValue())) {
          saveExecutionLogSafely(
              logCallback, color(format(k8sNamespaceLogLine, rancherInfrastructure.getNamespace().getValue()), Yellow));
        }
        validateK8sRancherInfrastructure(rancherInfrastructure);
        break;

      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }
  }

  private void validateK8sDirectInfrastructure(K8SDirectInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_NAMESPACE, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getNamespace(), K8S_NAMESPACE);

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of(K8S_RELEASE_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getReleaseName(), K8S_RELEASE_NAME);
  }

  private void validateK8sGcpInfrastructure(K8sGcpInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_NAMESPACE, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getNamespace(), K8S_NAMESPACE);

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of(K8S_RELEASE_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getReleaseName(), K8S_RELEASE_NAME);

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_CLUSTER_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getCluster(), K8S_CLUSTER_NAME);
  }

  private void validateRuntimeInputExpression(ParameterField<String> fieldValue, String fieldType) {
    if (fieldValue != null && INPUT_EXPRESSION.equals(fieldValue.fetchFinalValue())) {
      throw new InvalidArgumentsException(Pair.of(fieldType, NOT_PROVIDED_ERROR_MSG));
    }
  }

  private void validateK8sAzureInfrastructure(K8sAzureInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_NAMESPACE, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getNamespace(), K8S_NAMESPACE);

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of(K8S_RELEASE_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getReleaseName(), K8S_RELEASE_NAME);

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_CLUSTER_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getCluster(), K8S_CLUSTER_NAME);

    if (ParameterField.isNull(infrastructure.getSubscriptionId())
        || isEmpty(getParameterFieldValue(infrastructure.getSubscriptionId()))) {
      throw new InvalidArgumentsException(Pair.of(SUBSCRIPTION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getSubscriptionId(), SUBSCRIPTION);

    if (ParameterField.isNull(infrastructure.getResourceGroup())
        || isEmpty(getParameterFieldValue(infrastructure.getResourceGroup()))) {
      throw new InvalidArgumentsException(Pair.of(RESOURCE_GROUP, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getResourceGroup(), RESOURCE_GROUP);
  }

  private void validateAzureWebAppInfrastructure(AzureWebAppInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getConnectorRef())
        || isEmpty(getParameterFieldValue(infrastructure.getConnectorRef()))) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (ParameterField.isNull(infrastructure.getSubscriptionId())
        || isEmpty(getParameterFieldValue(infrastructure.getSubscriptionId()))) {
      throw new InvalidArgumentsException(Pair.of("subscription", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (ParameterField.isNull(infrastructure.getResourceGroup())
        || isEmpty(getParameterFieldValue(infrastructure.getResourceGroup()))) {
      throw new InvalidArgumentsException(Pair.of("resourceGroup", CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validatePdcInfrastructure(PdcInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (infrastructure.isDynamicallyProvisioned()) {
      validateDynamicPdcInfrastructure(infrastructure);
    } else {
      validatePdcInfrastructure(infrastructure.getHosts(), infrastructure.getConnectorRef());
    }
  }

  private void validatePdcInfrastructure(ParameterField<List<String>> hosts, ParameterField<String> connectorRef) {
    if (!hasValueListOrExpression(hosts) && !hasValueOrExpression(connectorRef)) {
      throw new InvalidArgumentsException(Pair.of("hosts", CANNOT_BE_EMPTY_ERROR_MSG),
          Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG),
          new IllegalArgumentException("hosts and connectorRef are not defined"));
    }
  }

  private void validateDynamicPdcInfrastructure(PdcInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getHostArrayPath(), false)) {
      throw new InvalidArgumentsException(Pair.of("hostArrayPath", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getHostAttributes(), false)) {
      throw new InvalidArgumentsException(Pair.of("hostAttributes", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    ParameterField<Map<String, String>> hostAttributes = infrastructure.getHostAttributes();
    if (ParameterField.isNull(hostAttributes) || hostAttributes.getValue() == null
        || !hostAttributes.getValue().containsKey(HOSTNAME_HOST_ATTRIBUTE)) {
      throw new InvalidRequestException(
          format("[%s] property is mandatory for getting host names", HOSTNAME_HOST_ATTRIBUTE));
    }
  }

  private void validateServerlessAwsInfrastructure(ServerlessAwsLambdaInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getRegion())
        || isEmpty(getParameterFieldValue(infrastructure.getRegion()))) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getStage())) {
      throw new InvalidArgumentsException(Pair.of("stage", CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateSshWinRmAzureInfrastructure(SshWinRmAzureInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getSubscriptionId())) {
      throw new InvalidArgumentsException(Pair.of("subscriptionId", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getResourceGroup())) {
      throw new InvalidArgumentsException(Pair.of("resourceGroup", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateSshWinRmAwsInfrastructure(SshWinRmAwsInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getHostConnectionType())) {
      throw new InvalidArgumentsException(Pair.of("hostConnectionType", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (infrastructure.getAwsInstanceFilter() == null) {
      throw new InvalidArgumentsException(Pair.of("awsInstanceFilter", "cannot be null"));
    }
  }

  private void validateEcsInfrastructure(EcsInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getCluster())) {
      throw new InvalidArgumentsException(Pair.of(K8S_CLUSTER_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateAwsSamInfrastructure(AwsSamInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }
    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateGoogleFunctionsInfrastructure(GoogleFunctionsInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getProject())) {
      throw new InvalidArgumentsException(Pair.of("project", "cannot be empty"));
    }
    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, "cannot be empty"));
    }
  }

  private void validateElastigroupInfrastructure(ElastigroupInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (null == infrastructure.getConfiguration()) {
      throw new InvalidArgumentsException(Pair.of("configuration", CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateTanzuApplicationServiceInfrastructure(TanzuApplicationServiceInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (!hasValueOrExpression(infrastructure.getOrganization())) {
      throw new InvalidArgumentsException(Pair.of("Organization", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (!hasValueOrExpression(infrastructure.getSpace())) {
      throw new InvalidArgumentsException(Pair.of("Space", CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateAsgInfrastructure(AsgInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("connectorRef", CANNOT_BE_EMPTY_ERROR_MSG));
    }

    if (!hasValueOrExpression(infrastructure.getRegion())) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateAwsLambdaInfrastructure(AwsLambdaInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getRegion())
        || isEmpty(getParameterFieldValue(infrastructure.getRegion()))) {
      throw new InvalidArgumentsException(Pair.of(AWS_REGION, CANNOT_BE_EMPTY_ERROR_MSG));
    }
  }

  private void validateK8sAwsInfrastructure(K8sAwsInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_NAMESPACE, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getNamespace(), K8S_NAMESPACE);

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of(K8S_RELEASE_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getReleaseName(), K8S_RELEASE_NAME);

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_CLUSTER_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getCluster(), K8S_CLUSTER_NAME);
  }

  private void validateK8sRancherInfrastructure(K8sRancherInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_NAMESPACE, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getNamespace(), K8S_NAMESPACE);

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of(K8S_RELEASE_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getReleaseName(), K8S_RELEASE_NAME);

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of(K8S_CLUSTER_NAME, CANNOT_BE_EMPTY_ERROR_MSG));
    }
    validateRuntimeInputExpression(infrastructure.getCluster(), K8S_CLUSTER_NAME);
  }

  @SafeVarargs
  public final <T> void validateExpression(ParameterField<T>... inputs) {
    for (ParameterField<T> input : inputs) {
      if (unresolvedExpression(input)) {
        throw new InvalidRequestException(format("Unresolved Expression : [%s]", input.getExpressionValue()));
      }
    }
  }

  private <T> boolean unresolvedExpression(ParameterField<T> input) {
    return !ParameterField.isNull(input) && input.isExpression() && input.getValue() == null;
  }

  public void requireOne(ParameterField<?> first, ParameterField<?> second) {
    if (unresolvedExpression(first) && unresolvedExpression(second)) {
      throw new InvalidRequestException(
          format("Unresolved Expressions : [%s] , [%s]", first.getExpressionValue(), second.getExpressionValue()));
    }
  }

  public void resolveProvisionerExpressions(Ambiance ambiance, Infrastructure infrastructure) {
    ProvisionerExpressionEvaluator expressionEvaluator =
        providerExpressionEvaluatorProvider.getProviderExpressionEvaluator(
            ambiance, infrastructure.getProvisionerStepIdentifier());

    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            k8SDirectInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8SDirectInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            k8sGcpInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sGcpInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sGcpInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            serverlessAwsLambdaInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            serverlessAwsLambdaInfrastructure.getStage(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            k8sAzureInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sAzureInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sAzureInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sAzureInfrastructure.getSubscriptionId(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sAzureInfrastructure.getResourceGroup(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;

        List<Map<String, Object>> hostObjects = (List<Map<String, Object>>) expressionEvaluator.evaluateExpression(
            getParameterFieldValue(pdcInfrastructure.getHostArrayPath()), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        if (isNotEmpty(hostObjects)) {
          for (Object hostObject : hostObjects) {
            expressionEvaluator.evaluateProperties(
                getParameterFieldValue(pdcInfrastructure.getHostAttributes()), (Map<String, Object>) hostObject);
          }
        }
        break;

      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            sshWinRmAwsInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.evaluateExpression(
            sshWinRmAwsInfrastructure.getAwsInstanceFilter().getTags(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            sshWinRmAzureInfrastructure.getSubscriptionId(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            sshWinRmAzureInfrastructure.getResourceGroup(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.evaluateExpression(
            sshWinRmAzureInfrastructure.getTags(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            azureWebAppInfrastructure.getSubscriptionId(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            azureWebAppInfrastructure.getResourceGroup(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            ecsInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            ecsInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        GoogleFunctionsInfrastructure googleFunctionsInfrastructure = (GoogleFunctionsInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            googleFunctionsInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            googleFunctionsInfrastructure.getProject(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.ELASTIGROUP:
        break;

      case InfrastructureKind.ASG:
        AsgInfrastructure asgInfrastructure = (AsgInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            asgInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        break;

      case InfrastructureKind.TAS:
        TanzuApplicationServiceInfrastructure tanzuInfrastructure =
            (TanzuApplicationServiceInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            tanzuInfrastructure.getOrganization(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            tanzuInfrastructure.getSpace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.AWS_SAM:
        AwsSamInfrastructure awsSamInfrastructure = (AwsSamInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            awsSamInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.AWS_LAMBDA:
        AwsLambdaInfrastructure awsLambdaInfrastructure = (AwsLambdaInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            awsLambdaInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            k8sAwsInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sAwsInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            k8sAwsInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        expressionEvaluator.resolveExpression(
            rancherInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            rancherInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolveExpression(
            rancherInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;
    }
  }
}
