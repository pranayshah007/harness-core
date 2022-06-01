/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sAzureInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.beans.PdcInfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.common.ParameterFieldHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.List;
import javax.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class InfrastructureMapper {
  public InfrastructureOutcome toOutcome(
      @Nonnull Infrastructure infrastructure, EnvironmentOutcome environmentOutcome, ServiceStepOutcome service) {
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        validateK8sDirectInfrastructure(k8SDirectInfrastructure);
        return K8sDirectInfrastructureOutcome.builder()
            .connectorRef(k8SDirectInfrastructure.getConnectorRef().getValue())
            .namespace(k8SDirectInfrastructure.getNamespace().getValue())
            .releaseName(getValueOrExpression(k8SDirectInfrastructure.getReleaseName()))
            .environment(environmentOutcome)
            .infrastructureKey(InfrastructureKey.generate(
                service, environmentOutcome, k8SDirectInfrastructure.getInfrastructureKeyValues()))
            .build();

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        validateK8sGcpInfrastructure(k8sGcpInfrastructure);
        return K8sGcpInfrastructureOutcome.builder()
            .connectorRef(k8sGcpInfrastructure.getConnectorRef().getValue())
            .namespace(k8sGcpInfrastructure.getNamespace().getValue())
            .cluster(k8sGcpInfrastructure.getCluster().getValue())
            .releaseName(getValueOrExpression(k8sGcpInfrastructure.getReleaseName()))
            .environment(environmentOutcome)
            .infrastructureKey(InfrastructureKey.generate(
                service, environmentOutcome, k8sGcpInfrastructure.getInfrastructureKeyValues()))
            .build();
      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        validateServerlessAwsInfrastructure(serverlessAwsLambdaInfrastructure);
        return ServerlessAwsLambdaInfrastructureOutcome.builder()
            .connectorRef(serverlessAwsLambdaInfrastructure.getConnectorRef().getValue())
            .region(serverlessAwsLambdaInfrastructure.getRegion().getValue())
            .stage(serverlessAwsLambdaInfrastructure.getStage().getValue())
            .environment(environmentOutcome)
            .infrastructureKey(InfrastructureKey.generate(
                service, environmentOutcome, serverlessAwsLambdaInfrastructure.getInfrastructureKeyValues()))
            .build();

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        validateK8sAzureInfrastructure(k8sAzureInfrastructure);
        return K8sAzureInfrastructureOutcome.builder()
            .connectorRef(k8sAzureInfrastructure.getConnectorRef().getValue())
            .namespace(k8sAzureInfrastructure.getNamespace().getValue())
            .cluster(k8sAzureInfrastructure.getCluster().getValue())
            .releaseName(getValueOrExpression(k8sAzureInfrastructure.getReleaseName()))
            .environment(environmentOutcome)
            .infrastructureKey(InfrastructureKey.generate(
                service, environmentOutcome, k8sAzureInfrastructure.getInfrastructureKeyValues()))
            .subscription(k8sAzureInfrastructure.getSubscriptionId().getValue())
            .resourceGroup(k8sAzureInfrastructure.getResourceGroup().getValue())
            .build();

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
        validatePdcInfrastructure(pdcInfrastructure);
        return PdcInfrastructureOutcome.builder()
            .credentialsRef(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getCredentialsRef()))
            .hosts(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getHosts()))
            .connectorRef(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getConnectorRef()))
            .hostFilters(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getHostFilters()))
            .attributeFilters(ParameterFieldHelper.getParameterFieldValue(pdcInfrastructure.getAttributeFilters()))
            .environment(environmentOutcome)
            .infrastructureKey(
                InfrastructureKey.generate(service, environmentOutcome, pdcInfrastructure.getInfrastructureKeyValues()))
            .build();

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        validateSshWinRmAzureInfrastructure(sshWinRmAzureInfrastructure);
        return SshWinRmAzureInfrastructureOutcome.builder()
            .connectorRef(ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getConnectorRef()))
            .subscriptionId(
                ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getSubscriptionId()))
            .resourceGroup(ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getResourceGroup()))
            .credentialsRef(
                ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getCredentialsRef()))
            .tags(ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getTags()))
            .usePublicDns(ParameterFieldHelper.getParameterFieldValue(sshWinRmAzureInfrastructure.getUsePublicDns()))
            .environment(environmentOutcome)
            .infrastructureKey(InfrastructureKey.generate(
                service, environmentOutcome, sshWinRmAzureInfrastructure.getInfrastructureKeyValues()))
            .build();

      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }
  }

  private void validateK8sDirectInfrastructure(K8SDirectInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of("namespace", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "cannot be empty"));
    }
  }

  private void validateK8sGcpInfrastructure(K8sGcpInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of("namespace", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of("cluster", "cannot be empty"));
    }
  }

  private void validateK8sAzureInfrastructure(K8sAzureInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getNamespace())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getNamespace()))) {
      throw new InvalidArgumentsException(Pair.of("namespace", "cannot be empty"));
    }

    if (!hasValueOrExpression(infrastructure.getReleaseName())) {
      throw new InvalidArgumentsException(Pair.of("releaseName", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getCluster())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getCluster()))) {
      throw new InvalidArgumentsException(Pair.of("cluster", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getSubscriptionId())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getSubscriptionId()))) {
      throw new InvalidArgumentsException(Pair.of("subscription", "cannot be empty"));
    }

    if (ParameterField.isNull(infrastructure.getResourceGroup())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getResourceGroup()))) {
      throw new InvalidArgumentsException(Pair.of("resourceGroup", "cannot be empty"));
    }
  }

  private void validatePdcInfrastructure(PdcInfrastructure infrastructure) {
    if (!hasValueOrExpression(infrastructure.getCredentialsRef())) {
      throw new InvalidArgumentsException(Pair.of("credentialsRef", "cannot be empty"));
    }

    if (!notEmptyOrExpression(infrastructure.getHosts()) && !hasValueOrExpression(infrastructure.getConnectorRef())) {
      throw new InvalidArgumentsException(Pair.of("hosts", "cannot be empty"),
          Pair.of("connectorRef", "cannot be empty"),
          new IllegalArgumentException("hosts and connectorRef are not defined"));
    }
  }

  private void validateServerlessAwsInfrastructure(ServerlessAwsLambdaInfrastructure infrastructure) {
    if (ParameterField.isNull(infrastructure.getRegion())
        || isEmpty(ParameterFieldHelper.getParameterFieldValue(infrastructure.getRegion()))) {
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

  private boolean hasValueOrExpression(ParameterField<String> parameterField) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    return parameterField.isExpression() || !isEmpty(ParameterFieldHelper.getParameterFieldValue(parameterField));
  }

  private <T> boolean notEmptyOrExpression(ParameterField<List<T>> parameterField) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    return parameterField.isExpression() || !isEmpty(ParameterFieldHelper.getParameterFieldValue(parameterField));
  }

  private String getValueOrExpression(ParameterField<String> parameterField) {
    if (parameterField.isExpression()) {
      return parameterField.getExpressionValue();
    } else {
      return parameterField.getValue();
    }
  }
}
