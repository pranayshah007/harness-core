/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static software.wings.beans.LogColor.Green;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.infra.InfrastructureMapper;
import io.harness.cdng.infra.beans.InfraMapping;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sGcpInfrastructureOutcome;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sAzureInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.infra.yaml.PdcInfrastructure;
import io.harness.cdng.infra.yaml.ServerlessAwsLambdaInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAwsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.executable.SyncExecutableWithRbac;
import io.harness.steps.shellscript.K8sInfraDelegateConfigOutput;
import io.harness.steps.shellscript.SshInfraDelegateConfigOutput;
import io.harness.utils.IdentifierRefHelper;
import io.harness.walktree.visitor.SimpleVisitorFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@OwnedBy(CDC)
public class InfrastructureStep implements SyncExecutableWithRbac<Infrastructure> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.INFRASTRUCTURE.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureStepHelper infrastructureStepHelper;
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private OutcomeService outcomeService;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public Class<Infrastructure> getStepParametersClass() {
    return Infrastructure.class;
  }

  InfraMapping createInfraMappingObject(Infrastructure infrastructureSpec) {
    return infrastructureSpec.getInfraMapping();
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, Infrastructure infrastructure,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    long startTime = System.currentTimeMillis();

    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance, true);
    saveExecutionLog(logCallback, "Starting infrastructure step...");

    validateConnector(infrastructure, ambiance);

    saveExecutionLog(logCallback, "Fetching environment information...");

    validateInfrastructure(infrastructure, ambiance);
    EnvironmentOutcome environmentOutcome = (EnvironmentOutcome) executionSweepingOutputService.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.ENVIRONMENT));
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    InfrastructureOutcome infrastructureOutcome =
        InfrastructureMapper.toOutcome(infrastructure, environmentOutcome, serviceOutcome);

    if (environmentOutcome != null) {
      if (EmptyPredicate.isNotEmpty(environmentOutcome.getName())) {
        saveExecutionLog(logCallback, color(format("Environment Name: %s", environmentOutcome.getName()), Yellow));
      }

      if (environmentOutcome.getType() != null && EmptyPredicate.isNotEmpty(environmentOutcome.getType().name())) {
        saveExecutionLog(
            logCallback, color(format("Environment Type: %s", environmentOutcome.getType().name()), Yellow));
      }
    }

    if (infrastructureOutcome != null && EmptyPredicate.isNotEmpty(infrastructureOutcome.getKind())) {
      saveExecutionLog(
          logCallback, color(format("Infrastructure Definition Type: %s", infrastructureOutcome.getKind()), Yellow));
    }

    saveExecutionLog(logCallback, color("Environment information fetched", Green));

    publishInfraDelegateConfigOutput(serviceOutcome, infrastructureOutcome, ambiance);

    if (logCallback != null) {
      logCallback.saveExecutionLog(
          color("Completed infrastructure step", Green), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepOutcome.builder()
                         .outcome(infrastructureOutcome)
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .group(OutcomeExpressionConstants.INFRASTRUCTURE_GROUP)
                         .build())
        .unitProgressList(Collections.singletonList(UnitProgress.newBuilder()
                                                        .setUnitName("Execute")
                                                        .setStatus(UnitStatus.SUCCESS)
                                                        .setStartTime(startTime)
                                                        .setEndTime(System.currentTimeMillis())
                                                        .build()))
        .build();
  }

  private void publishInfraDelegateConfigOutput(
      ServiceStepOutcome serviceOutcome, InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    if (ServiceSpecType.SSH.equals(serviceOutcome.getType())) {
      publishSshInfraDelegateConfigOutput(infrastructureOutcome, ambiance);
    }

    if (infrastructureOutcome instanceof K8sGcpInfrastructureOutcome
        || infrastructureOutcome instanceof K8sDirectInfrastructureOutcome) {
      K8sInfraDelegateConfig k8sInfraDelegateConfig =
          k8sStepHelper.getK8sInfraDelegateConfig(infrastructureOutcome, ambiance);

      K8sInfraDelegateConfigOutput k8sInfraDelegateConfigOutput =
          K8sInfraDelegateConfigOutput.builder().k8sInfraDelegateConfig(k8sInfraDelegateConfig).build();
      executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.K8S_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
          k8sInfraDelegateConfigOutput, StepOutcomeGroup.STAGE.name());
    }
  }

  private void publishSshInfraDelegateConfigOutput(InfrastructureOutcome infrastructureOutcome, Ambiance ambiance) {
    SshInfraDelegateConfig sshInfraDelegateConfig =
        k8sStepHelper.getSshInfraDelegateConfig(infrastructureOutcome, ambiance);

    SshInfraDelegateConfigOutput sshInfraDelegateConfigOutput =
        SshInfraDelegateConfigOutput.builder().sshInfraDelegateConfig(sshInfraDelegateConfig).build();
    executionSweepingOutputService.consume(ambiance, OutputExpressionConstants.SSH_INFRA_DELEGATE_CONFIG_OUTPUT_NAME,
        sshInfraDelegateConfigOutput, StepOutcomeGroup.STAGE.name());
  }

  @VisibleForTesting
  void validateConnector(Infrastructure infrastructure, Ambiance ambiance) {
    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance);

    if (infrastructure == null) {
      return;
    }

    if (InfrastructureKind.PDC.equals(infrastructure.getKind())
        && ParameterField.isNull(infrastructure.getConnectorReference())) {
      return;
    }

    ConnectorInfoDTO connectorInfo = validateAndGetConnector(infrastructure.getConnectorReference(), ambiance);

    if (InfrastructureKind.KUBERNETES_GCP.equals(infrastructure.getKind())) {
      if (!(connectorInfo.getConnectorConfig() instanceof GcpConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.GCP.name()));
      }
    }

    if (InfrastructureKind.SERVERLESS_AWS_LAMBDA.equals(infrastructure.getKind())) {
      if (!(connectorInfo.getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
            connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
            ConnectorType.AWS.name()));
      }

      AwsConnectorDTO awsConnector = (AwsConnectorDTO) connectorInfo.getConnectorConfig();
      if (AwsCredentialType.MANUAL_CREDENTIALS != awsConnector.getCredential().getAwsCredentialType()) {
        throw new InvalidRequestException(
            "Deployment using AWS infrastructure with manual credentials is only supported.");
      }
    }

    if (InfrastructureKind.KUBERNETES_AZURE.equals(infrastructure.getKind())
        && !(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.SSH_WINRM_AZURE.equals(infrastructure.getKind())
        && !(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    if (InfrastructureKind.AZURE_WEB_APP.equals(infrastructure.getKind())
        && !(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidRequestException(format("Invalid connector type [%s] for identifier: [%s], expected [%s]",
          connectorInfo.getConnectorType().name(), infrastructure.getConnectorReference().getValue(),
          ConnectorType.AZURE.name()));
    }

    saveExecutionLog(logCallback, color("Connector validated", Green));
  }

  private ConnectorInfoDTO validateAndGetConnector(ParameterField<String> connectorRef, Ambiance ambiance) {
    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance);
    saveExecutionLog(logCallback, "Fetching and validating connector...");

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (ParameterField.isNull(connectorRef)) {
      throw new InvalidRequestException("Connector ref field not present in infrastructure");
    }
    String connectorRefValue = connectorRef.getValue();
    IdentifierRef connectorIdentifierRef = IdentifierRefHelper.getIdentifierRef(connectorRefValue,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.get(connectorIdentifierRef.getAccountIdentifier(), connectorIdentifierRef.getOrgIdentifier(),
            connectorIdentifierRef.getProjectIdentifier(), connectorIdentifierRef.getIdentifier());
    ConnectorInfoDTO connectorInfoDTO;
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorRefValue));
    } else {
      saveExecutionLog(logCallback, color("Connector fetched", Green));

      connectorInfoDTO = connectorDTO.get().getConnector();
      if (connectorInfoDTO != null) {
        if (EmptyPredicate.isNotEmpty(connectorInfoDTO.getName())) {
          saveExecutionLog(logCallback, color(format("Connector Name: %s", connectorInfoDTO.getName()), Yellow));
        }

        if (connectorInfoDTO.getConnectorType() != null
            && EmptyPredicate.isNotEmpty(connectorInfoDTO.getConnectorType().name())) {
          saveExecutionLog(
              logCallback, color(format("Connector Type: %s", connectorInfoDTO.getConnectorType().name()), Yellow));
        }
      }

      ConnectorConnectivityDetails connectorConnectivityDetails = connectorDTO.get().getStatus();
      if (connectorConnectivityDetails != null && connectorConnectivityDetails.getStatus() != null
          && EmptyPredicate.isNotEmpty(connectorConnectivityDetails.getStatus().name())) {
        saveExecutionLog(logCallback,
            color(format("Connector Status: %s", connectorConnectivityDetails.getStatus().name()), Yellow));
      }
    }
    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());

    return connectorInfoDTO;
  }

  @VisibleForTesting
  void validateInfrastructure(Infrastructure infrastructure, Ambiance ambiance) {
    String k8sNamespaceLogLine = "Kubernetes Namespace: %s";

    NGLogCallback logCallback = infrastructureStepHelper.getInfrastructureLogCallback(ambiance);

    if (infrastructure == null) {
      throw new InvalidRequestException("Infrastructure definition can't be null or empty");
    }
    switch (infrastructure.getKind()) {
      case InfrastructureKind.KUBERNETES_DIRECT:
        K8SDirectInfrastructure k8SDirectInfrastructure = (K8SDirectInfrastructure) infrastructure;
        validateExpression(k8SDirectInfrastructure.getConnectorRef(), k8SDirectInfrastructure.getNamespace());

        if (k8SDirectInfrastructure.getNamespace() != null
            && EmptyPredicate.isNotEmpty(k8SDirectInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(logCallback,
              color(format(k8sNamespaceLogLine, k8SDirectInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;

      case InfrastructureKind.KUBERNETES_GCP:
        K8sGcpInfrastructure k8sGcpInfrastructure = (K8sGcpInfrastructure) infrastructure;
        validateExpression(k8sGcpInfrastructure.getConnectorRef(), k8sGcpInfrastructure.getNamespace(),
            k8sGcpInfrastructure.getCluster());

        if (k8sGcpInfrastructure.getNamespace() != null
            && EmptyPredicate.isNotEmpty(k8sGcpInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(
              logCallback, color(format(k8sNamespaceLogLine, k8sGcpInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;
      case InfrastructureKind.SERVERLESS_AWS_LAMBDA:
        ServerlessAwsLambdaInfrastructure serverlessAwsLambdaInfrastructure =
            (ServerlessAwsLambdaInfrastructure) infrastructure;
        validateExpression(serverlessAwsLambdaInfrastructure.getConnectorRef(),
            serverlessAwsLambdaInfrastructure.getRegion(), serverlessAwsLambdaInfrastructure.getStage());
        break;

      case InfrastructureKind.KUBERNETES_AZURE:
        K8sAzureInfrastructure k8sAzureInfrastructure = (K8sAzureInfrastructure) infrastructure;
        validateExpression(k8sAzureInfrastructure.getConnectorRef(), k8sAzureInfrastructure.getNamespace(),
            k8sAzureInfrastructure.getCluster(), k8sAzureInfrastructure.getSubscriptionId(),
            k8sAzureInfrastructure.getResourceGroup());

        if (k8sAzureInfrastructure.getNamespace() != null
            && EmptyPredicate.isNotEmpty(k8sAzureInfrastructure.getNamespace().getValue())) {
          saveExecutionLog(logCallback,
              color(format(k8sNamespaceLogLine, k8sAzureInfrastructure.getNamespace().getValue()), Yellow));
        }
        break;

      case InfrastructureKind.SSH_WINRM_AZURE:
        SshWinRmAzureInfrastructure sshWinRmAzureInfrastructure = (SshWinRmAzureInfrastructure) infrastructure;
        validateExpression(sshWinRmAzureInfrastructure.getConnectorRef(),
            sshWinRmAzureInfrastructure.getSubscriptionId(), sshWinRmAzureInfrastructure.getResourceGroup(),
            sshWinRmAzureInfrastructure.getCredentialsRef());
        break;

      case InfrastructureKind.PDC:
        PdcInfrastructure pdcInfrastructure = (PdcInfrastructure) infrastructure;
        validateExpression(pdcInfrastructure.getCredentialsRef());
        requireOne(pdcInfrastructure.getHosts(), pdcInfrastructure.getConnectorRef());
        break;
      case InfrastructureKind.SSH_WINRM_AWS:
        SshWinRmAwsInfrastructure sshWinRmAwsInfrastructure = (SshWinRmAwsInfrastructure) infrastructure;
        validateExpression(sshWinRmAwsInfrastructure.getConnectorRef(), sshWinRmAwsInfrastructure.getCredentialsRef(),
            sshWinRmAwsInfrastructure.getRegion());
        break;

      case InfrastructureKind.AZURE_WEB_APP:
        AzureWebAppInfrastructure azureWebAppInfrastructure = (AzureWebAppInfrastructure) infrastructure;
        validateExpression(azureWebAppInfrastructure.getConnectorRef(), azureWebAppInfrastructure.getAppService(),
            azureWebAppInfrastructure.getDeploymentSlot(), azureWebAppInfrastructure.getSubscriptionId(),
            azureWebAppInfrastructure.getResourceGroup());
        break;
      default:
        throw new InvalidArgumentsException(format("Unknown Infrastructure Kind : [%s]", infrastructure.getKind()));
    }
  }

  @SafeVarargs
  private final <T> void validateExpression(ParameterField<T>... inputs) {
    for (ParameterField<T> input : inputs) {
      if (unresolvedExpression(input)) {
        throw new InvalidRequestException(format("Unresolved Expression : [%s]", input.getExpressionValue()));
      }
    }
  }

  private <T> boolean unresolvedExpression(ParameterField<T> input) {
    return !ParameterField.isNull(input) && input.isExpression();
  }

  private void requireOne(ParameterField<?> first, ParameterField<?> second) {
    if (unresolvedExpression(first) && unresolvedExpression(second)) {
      throw new InvalidRequestException(
          format("Unresolved Expressions : [%s] , [%s]", first.getExpressionValue(), second.getExpressionValue()));
    }
  }

  @Override
  public void validateResources(Ambiance ambiance, Infrastructure infrastructure) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      return;
    }
    Set<EntityDetailProtoDTO> entityDetails =
        entityReferenceExtractorUtils.extractReferredEntities(ambiance, infrastructure);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails);
  }

  private void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, null);
  }
}
