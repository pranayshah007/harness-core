/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.AsgInfrastructure;
import io.harness.cdng.infra.yaml.AwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAwsInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.K8sRancherInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.infra.yaml.TanzuApplicationServiceInfrastructure;
import io.harness.evaluators.ProvisionerExpressionEvaluator;
import io.harness.evaluators.ProvisionerExpressionEvaluatorProvider;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class InfrastructureProvisionerHelper {
  @Inject private ProvisionerExpressionEvaluatorProvider provisionerExpressionEvaluatorProvider;

  public void resolveProvisionerExpressions(Ambiance ambiance, Infrastructure infrastructure) {
    ProvisionerExpressionEvaluator expressionEvaluator =
        provisionerExpressionEvaluatorProvider.getProvisionerExpressionEvaluator(
            ambiance, infrastructure.getProvisionerStepIdentifier());

    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            k8SDirectInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            k8SDirectInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        expressionEvaluator.resolve(k8sGcpInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(k8sGcpInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            k8sGcpInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            serverlessAwsLambdaInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            serverlessAwsLambdaInfrastructure.getStage(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            k8sAzureInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(k8sAzureInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            k8sAzureInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            k8sAzureInfrastructure.getSubscriptionId(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            k8sAzureInfrastructure.getResourceGroup(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.PDC:
        /*
        Since resolving provisioner expressions for pdc is a complex scenario, we handle it separately in
        PdcProvisionedInfrastructureMapper class. We resolve the provisioner expressions and generate
        PdcInfrastructureOutcome there.
         */
        break;

      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            sshWinRmAwsInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            sshWinRmAwsInfrastructure.getAwsInstanceFilter().getTags(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            sshWinRmAzureInfrastructure.getSubscriptionId(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            sshWinRmAzureInfrastructure.getResourceGroup(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(sshWinRmAzureInfrastructure.getTags(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            azureWebAppInfrastructure.getSubscriptionId(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            azureWebAppInfrastructure.getResourceGroup(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.ECS:
        EcsInfrastructure ecsInfrastructure = (EcsInfrastructure) infrastructure;
        expressionEvaluator.resolve(ecsInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(ecsInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.GOOGLE_CLOUD_FUNCTIONS:
        GoogleFunctionsInfrastructure googleFunctionsInfrastructure = (GoogleFunctionsInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            googleFunctionsInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            googleFunctionsInfrastructure.getProject(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.ELASTIGROUP:
        break;

      case InfrastructureKind.ASG:
        AsgInfrastructure asgInfrastructure = (AsgInfrastructure) infrastructure;
        expressionEvaluator.resolve(asgInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.CUSTOM_DEPLOYMENT:
        break;

      case InfrastructureKind.TAS:
        TanzuApplicationServiceInfrastructure tanzuInfrastructure =
            (TanzuApplicationServiceInfrastructure) infrastructure;
        expressionEvaluator.resolve(
            tanzuInfrastructure.getOrganization(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(tanzuInfrastructure.getSpace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.AWS_SAM:
        AwsSamInfrastructure awsSamInfrastructure = (AwsSamInfrastructure) infrastructure;
        expressionEvaluator.resolve(awsSamInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.AWS_LAMBDA:
        AwsLambdaInfrastructure awsLambdaInfrastructure = (AwsLambdaInfrastructure) infrastructure;
        expressionEvaluator.resolve(awsLambdaInfrastructure.getRegion(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_AWS:
        K8sAwsInfrastructure k8sAwsInfrastructure = (K8sAwsInfrastructure) infrastructure;
        expressionEvaluator.resolve(k8sAwsInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(k8sAwsInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            k8sAwsInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      case InfrastructureKind.KUBERNETES_RANCHER:
        K8sRancherInfrastructure rancherInfrastructure = (K8sRancherInfrastructure) infrastructure;
        expressionEvaluator.resolve(rancherInfrastructure.getNamespace(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(rancherInfrastructure.getCluster(), ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
        expressionEvaluator.resolve(
            rancherInfrastructure.getReleaseName(), ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        break;

      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }
  }
}
