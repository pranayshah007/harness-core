/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static software.wings.beans.command.KubernetesSetupParams.KubernetesSetupParamsBuilder.aKubernetesSetupParams;
import static software.wings.sm.StateType.KUBERNETES_SETUP_ROLLBACK;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.ExecutionStatus;
import io.harness.context.ContextElementType;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.k8s.model.ImageDetails;

import software.wings.api.CommandStateExecutionData;
import software.wings.api.ContainerRollbackRequestElement;
import software.wings.api.ContainerServiceElement;
import software.wings.api.ContainerServiceElement.ContainerServiceElementBuilder;
import software.wings.api.DeploymentType;
import software.wings.api.PhaseElement;
import software.wings.beans.Application;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.command.ContainerSetupCommandUnitExecutionData;
import software.wings.beans.command.ContainerSetupParams;
import software.wings.beans.command.KubernetesSetupParams;
import software.wings.beans.container.ContainerTask;
import software.wings.beans.container.ContainerTaskMapper;
import software.wings.beans.container.KubernetesContainerTask;
import software.wings.sm.ExecutionContext;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by brett on 12/18/17
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KubernetesSetupRollback extends ContainerServiceSetup {
  private String commandName = "Setup Replication Controller";

  public KubernetesSetupRollback(String name) {
    super(name, KUBERNETES_SETUP_ROLLBACK.name());
  }

  @Override
  protected ContainerSetupParams buildContainerSetupParams(ExecutionContext context, String serviceName,
      ImageDetails imageDetails, Application app, Environment env, Service service,
      ContainerInfrastructureMapping infrastructureMapping, ContainerTask containerTask, String clusterName) {
    ContainerRollbackRequestElement rollbackElement = context.getContextElement(
        ContextElementType.PARAM, ContainerRollbackRequestElement.CONTAINER_ROLLBACK_REQUEST_PARAM);

    String subscriptionId = null;
    String resourceGroup = null;

    if (infrastructureMapping instanceof AzureKubernetesInfrastructureMapping) {
      subscriptionId = ((AzureKubernetesInfrastructureMapping) infrastructureMapping).getSubscriptionId();
      resourceGroup = ((AzureKubernetesInfrastructureMapping) infrastructureMapping).getResourceGroup();
    }

    String namespace = isNotBlank(infrastructureMapping.getNamespace())
        ? context.renderExpression(infrastructureMapping.getNamespace())
        : "default";

    int serviceSteadyStateTimeout =
        getServiceSteadyStateTimeout() > 0 ? getServiceSteadyStateTimeout() : DEFAULT_STEADY_STATE_TIMEOUT;
    boolean useNewLabelMechanism = true;

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    ContainerServiceElement containerElement =
        context.<ContainerServiceElement>getContextElementList(ContextElementType.CONTAINER_SERVICE)
            .stream()
            .filter(cse -> phaseElement.getDeploymentType().equals(cse.getDeploymentType().name()))
            .filter(cse -> context.fetchInfraMappingId().equals(cse.getInfraMappingId()))
            .findFirst()
            .orElse(ContainerServiceElement.builder().build());

    return aKubernetesSetupParams()
        .withAppName(app.getName())
        .withEnvName(env.getName())
        .withServiceName(serviceName)
        .withClusterName(clusterName)
        .withImageDetails(imageDetails)
        .withNamespace(namespace)
        .withContainerTask(ContainerTaskMapper.toKubernetesContainerTaskDTO((KubernetesContainerTask) containerTask))
        .withControllerNamePrefix(rollbackElement.getControllerNamePrefix())
        .withInfraMappingId(infrastructureMapping.getUuid())
        .withServiceSteadyStateTimeout(serviceSteadyStateTimeout)
        .withRollback(true)
        .withSubscriptionId(subscriptionId)
        .withResourceGroup(resourceGroup)
        .withUseNewLabelMechanism(useNewLabelMechanism)
        .withReleaseName(rollbackElement.getReleaseName())
        .withServiceCounts(containerElement.getActiveServiceCounts())
        .build();
  }

  @Override
  protected ContainerServiceElement buildContainerServiceElement(ExecutionContext context,
      CommandExecutionResult executionResult, ExecutionStatus status, ImageDetails imageDetails) {
    CommandStateExecutionData executionData = (CommandStateExecutionData) context.getStateExecutionData();
    KubernetesSetupParams setupParams = (KubernetesSetupParams) executionData.getContainerSetupParams();
    ContainerServiceElementBuilder containerServiceElementBuilder =
        ContainerServiceElement.builder()
            .uuid(executionData.getServiceId())
            .image(imageDetails.getName() + ":" + imageDetails.getTag())
            .clusterName(executionData.getClusterName())
            .namespace(setupParams.getNamespace())
            .deploymentType(DeploymentType.KUBERNETES)
            .infraMappingId(setupParams.getInfraMappingId())
            .activeServiceCounts(executionData.getServiceCounts());
    if (executionResult != null) {
      ContainerSetupCommandUnitExecutionData setupExecutionData =
          (ContainerSetupCommandUnitExecutionData) executionResult.getCommandExecutionData();
      if (setupExecutionData != null) {
        containerServiceElementBuilder.name(setupExecutionData.getContainerServiceName());
      }
    }
    return containerServiceElementBuilder.build();
  }

  @Override
  protected boolean isValidInfraMapping(InfrastructureMapping infrastructureMapping) {
    return infrastructureMapping instanceof GcpKubernetesInfrastructureMapping
        || infrastructureMapping instanceof AzureKubernetesInfrastructureMapping
        || infrastructureMapping instanceof DirectKubernetesInfrastructureMapping;
  }

  @Override
  protected String getDeploymentType() {
    return DeploymentType.KUBERNETES.name();
  }

  @Override
  public String getCommandName() {
    return commandName;
  }

  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }
}
