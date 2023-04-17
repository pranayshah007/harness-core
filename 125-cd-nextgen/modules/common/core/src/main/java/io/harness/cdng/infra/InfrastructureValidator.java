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

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
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
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

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

  public void validate(Infrastructure infrastructure) {
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        validateK8sDirectInfrastructure(k8SDirectInfrastructure);
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        validateK8sGcpInfrastructure(k8sGcpInfrastructure);
        break;

      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        validateServerlessAwsInfrastructure((ServerlessAwsLambdaInfrastructure) infrastructure);
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        validateK8sAzureInfrastructure(k8sAzureInfrastructure);
        break;

      case InfrastructureKind.PDC:
        validatePdcInfrastructure((PdcInfrastructure) infrastructure);
        break;

      case InfrastructureKind.SSH_WINRM_AWS:
        validateSshWinRmAwsInfrastructure((SshWinRmAwsInfrastructure) infrastructure);
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        validateSshWinRmAzureInfrastructure((SshWinRmAzureInfrastructure) infrastructure);
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        validateAzureWebAppInfrastructure((AzureWebAppInfrastructure) infrastructure);
        break;

      case InfrastructureKind.ECS:
        validateEcsInfrastructure((EcsInfrastructure) infrastructure);
        break;

      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        validateGoogleFunctionsInfrastructure((GoogleFunctionsInfrastructure) infrastructure);
        break;

      case InfrastructureKind.ELASTIGROUP:
        validateElastigroupInfrastructure((ElastigroupInfrastructure) infrastructure);
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        break;

      case InfrastructureKind.TAS:
        validateTanzuApplicationServiceInfrastructure((TanzuApplicationServiceInfrastructure) infrastructure);
        break;

      case InfrastructureKind.ASG:
        validateAsgInfrastructure((AsgInfrastructure) infrastructure);
        break;

      case InfrastructureKind.AWS_SAM:
        validateAwsSamInfrastructure((AwsSamInfrastructure) infrastructure);
        break;

      case InfrastructureKind.AWS_LAMBDA:
        validateAwsLambdaInfrastructure((AwsLambdaInfrastructure) infrastructure);
        break;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        validateK8sAwsInfrastructure(k8sAwsInfrastructure);
        break;

      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        validateK8sRancherInfrastructure(rancherInfrastructure);
        break;

      default:
        throw new InvalidArgumentsException(
            String.format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
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
}
