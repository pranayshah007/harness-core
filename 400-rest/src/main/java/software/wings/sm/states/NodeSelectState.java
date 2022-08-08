/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.beans.FeatureName.DEPLOY_TO_INLINE_HOSTS;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.ServiceInstanceIdsParam.ServiceInstanceIdsParamBuilder.aServiceInstanceIdsParam;
import static software.wings.beans.InstanceUnitType.COUNT;
import static software.wings.beans.InstanceUnitType.PERCENTAGE;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.beans.ServiceInstanceSelectionParams.Builder.aServiceInstanceSelectionParams;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.atteo.evo.inflector.English.plural;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.context.ContextElementType;
import io.harness.deployment.InstanceDetails;
import io.harness.deployment.InstanceDetails.InstanceDetailsBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.SelectedNodeExecutionData;
import software.wings.api.ServiceInstanceArtifactParam;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceInstanceSelectionParams;
import software.wings.beans.ServiceInstanceSelectionParams.Builder;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.common.InstanceExpressionProcessor;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by brett on 10/10/17
 */
@Slf4j
public abstract class NodeSelectState extends State {
  private static final int DEFAULT_CONCURRENT_EXECUTION_INSTANCE_LIMIT = 10;

  private String instanceCount;
  private InstanceUnitType instanceUnitType = COUNT;
  private boolean specificHosts;
  private List<String> hostNames;
  private boolean excludeSelectedHostsFromFuturePhases;

  @Inject @Transient private InfrastructureMappingService infrastructureMappingService;

  @Inject @Transient private AccountService accountService;

  @Inject @Transient private InstanceService instanceService;

  @Inject @Transient private ArtifactService artifactService;

  @Inject private transient StateExecutionService stateExecutionService;
  @Inject @Transient private FeatureFlagService featureFlagService;
  @Inject @Transient private AppService appService;
  @Inject @Transient private WorkflowExecutionService workflowExecutionService;
  @Inject @Transient private SweepingOutputService sweepingOutputService;
  @Inject @Transient private HostService hostService;
  @Inject @Transient private InstanceExpressionProcessor instanceExpressionProcessor;

  NodeSelectState(String name, String stateType) {
    super(name, stateType);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String appId = requireNonNull(context.getApp()).getUuid();
    String envId = context.fetchRequiredEnvironment().getUuid();

    PhaseElement phaseElement = context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String serviceId = phaseElement.getServiceElement().getUuid();
    String infraMappingId = context.fetchInfraMappingId();

    List<ServiceInstance> hostExclusionList = stateExecutionService.getHostExclusionList(
        ((ExecutionContextImpl) context).getStateExecutionInstance(), phaseElement, context.fetchInfraMappingId());

    List<String> excludedServiceInstanceIds =
        hostExclusionList.stream().map(ServiceInstance::getUuid).distinct().collect(toList());

    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    ServiceInstanceSelectionParams.Builder selectionParams =
        aServiceInstanceSelectionParams()
            .withExcludedServiceInstanceIds(excludedServiceInstanceIds)
            .withSelectSpecificHosts(specificHosts);
    int totalAvailableInstances =
        infrastructureMappingService.listHostDisplayNames(appId, infraMappingId, context.getWorkflowExecutionId())
            .size();
    int instancesToAdd;
    if (specificHosts) {
      if (infrastructureMapping instanceof AwsInfrastructureMapping
          && ((AwsInfrastructureMapping) infrastructureMapping).isProvisionInstances()) {
        throw new InvalidRequestException("Cannot specify hosts when using an auto scale group", WingsException.USER);
      }
      if (featureFlagService.isEnabled(DEPLOY_TO_INLINE_HOSTS, context.getAccountId()) && isNotEmpty(hostNames)) {
        hostNames = getResolvedHosts(context, hostNames);
      }
      selectionParams.withHostNames(hostNames);
      instancesToAdd = hostNames.size();
      log.info("Selecting specific hosts: {}", hostNames);
    } else {
      int instanceCountTotal = getCount(context, totalAvailableInstances);
      if (((ExecutionContextImpl) context).getStateExecutionInstance().getOrchestrationWorkflowType()
          == OrchestrationWorkflowType.ROLLING) {
        instancesToAdd = instanceCountTotal;
      } else {
        instancesToAdd = Math.max(0, instanceCountTotal - hostExclusionList.size());
      }
    }
    selectionParams.withCount(instancesToAdd);

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

    if (featureFlagService.isEnabled(DEPLOY_TO_INLINE_HOSTS, context.getAccountId())
        && isNotEmpty(workflowStandardParams.getExecutionHosts())) {
      workflowStandardParams.setExecutionHosts(getResolvedHosts(context, workflowStandardParams.getExecutionHosts()));
    }

    StringBuilder message = new StringBuilder();
    boolean nodesOverriddenFromExecutionHosts = processExecutionHosts(
        appId, selectionParams, workflowStandardParams, message, context.getWorkflowExecutionId());

    log.info("Selected {} instances - serviceId: {}, infraMappingId: {}", instancesToAdd, serviceId, infraMappingId);
    List<ServiceInstance> serviceInstances = infrastructureMappingService.selectServiceInstances(
        appId, infraMappingId, context.getWorkflowExecutionId(), selectionParams.build());

    ServiceInstanceSelectionParams selectionParamsForAllInstances =
        generateSelectionParamsForAllInstances(selectionParams, totalAvailableInstances);
    final List<ServiceInstance> instancesFromInfra = infrastructureMappingService.selectServiceInstances(
        appId, infraMappingId, context.getWorkflowExecutionId(), selectionParamsForAllInstances);

    String errorMessage = buildServiceInstancesErrorMessage(
        serviceInstances, hostExclusionList, infrastructureMapping, totalAvailableInstances, context);

    if (isNotEmpty(errorMessage) && !nodesOverriddenFromExecutionHosts) {
      return ExecutionResponse.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(errorMessage).build();
    }

    boolean excludeHostsWithSameArtifact = false;
    if (workflowStandardParams != null) {
      excludeHostsWithSameArtifact =
          workflowStandardParams.isExcludeHostsWithSameArtifact() && ROLLING != context.getOrchestrationWorkflowType();
      if (InfrastructureMappingType.AWS_SSH.name().equals(infrastructureMapping.getInfraMappingType())
          || InfrastructureMappingType.PHYSICAL_DATA_CENTER_SSH.name().equals(
              infrastructureMapping.getInfraMappingType())
          || InfrastructureMappingType.AZURE_INFRA.name().equals(infrastructureMapping.getInfraMappingType())
          || InfrastructureMappingType.PHYSICAL_DATA_CENTER_WINRM.name().equals(
              infrastructureMapping.getInfraMappingType())) {
        if (excludeHostsWithSameArtifact && !nodesOverriddenFromExecutionHosts) {
          serviceInstances =
              excludeHostsWithTheSameArtifactDeployed(context, appId, serviceId, infraMappingId, serviceInstances);
        }
      }
    }
    SelectedNodeExecutionData selectedNodeExecutionData = new SelectedNodeExecutionData();
    selectedNodeExecutionData.setServiceInstanceList(serviceInstances.stream()
                                                         .map(serviceInstance
                                                             -> aServiceInstance()
                                                                    .withUuid(serviceInstance.getUuid())
                                                                    .withHostId(serviceInstance.getHostId())
                                                                    .withHostName(serviceInstance.getHostName())
                                                                    .withPublicDns(serviceInstance.getPublicDns())
                                                                    .build())
                                                         .collect(toList()));
    selectedNodeExecutionData.setExcludeSelectedHostsFromFuturePhases(excludeSelectedHostsFromFuturePhases);
    List<String> serviceInstancesIds = serviceInstances.stream().map(ServiceInstance::getUuid).collect(toList());
    ServiceInstanceIdsParam serviceIdParamElement =
        aServiceInstanceIdsParam().withInstanceIds(serviceInstancesIds).withServiceId(serviceId).build();

    final List<InstanceElement> instanceElements =
        getInstanceElements(serviceInstances, instancesFromInfra, specificHosts);
    final List<InstanceDetails> instanceDetails =
        getInstanceDetails(appId, envId, serviceInstances, instancesFromInfra, specificHosts);
    sweepingOutputService.save(
        context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
            .name(ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS + phaseElement.getPhaseName().trim())
            .value(serviceIdParamElement)
            .build());

    boolean skipVerification = instanceDetails.stream().noneMatch(InstanceDetails::isNewInstance);
    sweepingOutputService.save(context.prepareSweepingOutputBuilder(Scope.WORKFLOW)
                                   .name(context.appendStateExecutionId(InstanceInfoVariables.SWEEPING_OUTPUT_NAME))
                                   .value(InstanceInfoVariables.builder()
                                              .instanceElements(instanceElements)
                                              .instanceDetails(instanceDetails)
                                              .skipVerification(skipVerification)
                                              .build())
                                   .build());

    ExecutionResponseBuilder executionResponse = ExecutionResponse.builder()
                                                     .contextElement(serviceIdParamElement)
                                                     .notifyElement(serviceIdParamElement)
                                                     .stateExecutionData(selectedNodeExecutionData);
    if (isEmpty(serviceInstances)) {
      if (!excludeHostsWithSameArtifact) {
        executionResponse.errorMessage("No nodes selected");
      } else {
        executionResponse.errorMessage("No nodes selected (Nodes already deployed with the same artifact)");
      }
    }
    if (nodesOverriddenFromExecutionHosts) {
      executionResponse.errorMessage(message.toString());
    }

    return executionResponse.build();
  }

  private List<String> getResolvedHosts(ExecutionContext context, List<String> hosts) {
    return hosts.stream()
        .map(context::renderExpression)
        .map(s -> Arrays.asList(s.split(",")))
        .flatMap(resolvedHosts -> resolvedHosts.stream().map(String::trim))
        .distinct()
        .collect(toList());
  }

  @VisibleForTesting
  List<InstanceElement> getInstanceElements(
      List<ServiceInstance> serviceInstances, List<ServiceInstance> allServiceInstances, boolean specificHosts) {
    if (specificHosts) {
      List<InstanceElement> instanceElements =
          emptyIfNull(instanceExpressionProcessor.convertToInstanceElements(serviceInstances));
      instanceElements.forEach(i -> i.setNewInstance(true));
      return instanceElements;
    }
    Set<String> newServiceInstanceIds =
        serviceInstances.stream().map(ServiceInstance::getUuid).collect(Collectors.toSet());
    List<InstanceElement> instanceElements =
        emptyIfNull(instanceExpressionProcessor.convertToInstanceElements(allServiceInstances));
    instanceElements.forEach(instanceElement -> {
      if (newServiceInstanceIds.contains(instanceElement.getUuid())) {
        instanceElement.setNewInstance(true);
      }
    });
    return instanceElements;
  }

  @VisibleForTesting
  ServiceInstanceSelectionParams generateSelectionParamsForAllInstances(
      Builder selectionParamsBuilder, int totalAvailableInstances) {
    return selectionParamsBuilder.but()
        .withCount(totalAvailableInstances)
        .withSelectSpecificHosts(false)
        .withHostNames(Collections.emptyList())
        .withExcludedServiceInstanceIds(new ArrayList<>())
        .build();
  }

  @VisibleForTesting
  List<InstanceDetails> getInstanceDetails(String appId, String envId, List<ServiceInstance> serviceInstances,
      List<ServiceInstance> allServiceInstances, boolean specificHosts) {
    List<InstanceDetails> instanceDetails =
        generateInstanceDetailsFromServiceInstances(serviceInstances, appId, envId, true);
    if (specificHosts) {
      return instanceDetails;
    }
    Set<String> newServiceInstanceIds =
        serviceInstances.stream().map(ServiceInstance::getUuid).collect(Collectors.toSet());
    List<ServiceInstance> oldServiceInstances =
        allServiceInstances.stream()
            .filter(serviceInstance -> !newServiceInstanceIds.contains(serviceInstance.getUuid()))
            .collect(toList());
    instanceDetails.addAll(generateInstanceDetailsFromServiceInstances(oldServiceInstances, appId, envId, false));
    return instanceDetails;
  }

  @VisibleForTesting
  List<InstanceDetails> generateInstanceDetailsFromServiceInstances(
      List<ServiceInstance> serviceInstances, String appId, String envId, boolean isNewInstance) {
    if (isNotEmpty(serviceInstances)) {
      List<String> hostIds = serviceInstances.stream().map(ServiceInstance::getHostId).collect(toList());
      List<Host> hosts = emptyIfNull(hostService.getHostsByHostIds(appId, envId, hostIds));
      return hosts.stream().map(host -> buildInstanceDetailFromHost(host, isNewInstance)).collect(toList());
    }
    return new ArrayList<>();
  }

  @VisibleForTesting
  InstanceDetails buildInstanceDetailFromHost(Host host, boolean isNewInstance) {
    final InstanceDetailsBuilder builder =
        InstanceDetails.builder().hostName(host.getHostName()).newInstance(isNewInstance);
    if (host.getEc2Instance() != null) {
      builder.instanceType(InstanceDetails.InstanceType.AWS);
      builder.aws(
          InstanceDetails.AWS.builder().ec2Instance(host.getEc2Instance()).publicDns(host.getPublicDns()).build());
    } else {
      builder.instanceType(InstanceDetails.InstanceType.PHYSICAL_HOST);
      builder.physicalHost(
          InstanceDetails.PHYSICAL_HOST.builder().instanceId(host.getUuid()).publicDns(host.getPublicDns()).build());
    }
    return builder.build();
  }

  boolean processExecutionHosts(String appId, Builder selectionParams, WorkflowStandardParams workflowStandardParams,
      StringBuilder message, String workflowExecutionId) {
    if (workflowStandardParams != null && isNotEmpty(workflowStandardParams.getExecutionHosts())
        && featureFlagService.isEnabled(FeatureName.DEPLOY_TO_SPECIFIC_HOSTS, appService.getAccountIdByAppId(appId))) {
      List<StateExecutionInstance> stateExecutionInstancesForPhases =
          workflowExecutionService.getStateExecutionInstancesForPhases(workflowExecutionId);
      if (stateExecutionInstancesForPhases.size() == 1) {
        message.append("Targeted nodes have overridden configured nodes");
        List<String> executionHosts = workflowStandardParams.getExecutionHosts();
        selectionParams.withSelectSpecificHosts(true);
        selectionParams.withHostNames(executionHosts);
        selectionParams.withCount(executionHosts.size());
      } else {
        message.append("No nodes selected as targeted nodes have already been deployed");
        selectionParams.withSelectSpecificHosts(true);
        selectionParams.withCount(0);
      }
      return true;
    }
    return false;
  }

  private int getCount(ExecutionContext context, int maxInstances) {
    if (instanceUnitType == PERCENTAGE) {
      int percent = Math.min(renderInstanceCount(context), 100);
      int percentInstanceCount = (int) Math.round(percent * maxInstances / 100.0);
      return Math.max(percentInstanceCount, 1);
    } else {
      return renderInstanceCount(context);
    }
  }

  private String buildServiceInstancesErrorMessage(List<ServiceInstance> serviceInstances,
      List<ServiceInstance> hostExclusionList, InfrastructureMapping infraMapping, int totalAvailableInstances,
      ExecutionContext context) {
    if (totalAvailableInstances == 0) {
      return "The service infrastructure [" + infraMapping.getDisplayName() + "] has no instances available.";
    }

    String errorMessage = null;
    if (isEmpty(serviceInstances)) {
      StringBuilder msg = new StringBuilder(256);
      msg.append("No nodes were selected. ");
      if (specificHosts) {
        msg.append("'Use Specific Hosts' was chosen ");
        if (isEmpty(hostNames)) {
          msg.append("but no host names were specified. ");
        } else {
          msg.append("with ").append(plural("host", hostNames.size())).append(' ').append(hostNames);
        }
      } else {
        if (instanceUnitType == PERCENTAGE) {
          return null;
        } else {
          msg.append("This phase deploys to ")
              .append(renderInstanceCount(context))
              .append(plural(" instance", renderInstanceCount(context)))
              .append(" (cumulative)");
        }
      }

      msg.append(" and ")
          .append(hostExclusionList.size())
          .append(hostExclusionList.size() == 1 ? " instance has" : " instances have")
          .append(" already been deployed. \n\n");

      if (isNotEmpty(hostNames)) {
        msg.append("The service infrastructure [")
            .append(infraMapping.getName())
            .append("] does not have ")
            .append((hostNames.size() == 1) ? "this host." : "these hosts.");
      }

      if (specificHosts) {
        msg.append("\n\nCheck whether you've selected a unique set of host names for each phase. ");
      } else if (infraMapping instanceof AwsInfrastructureMapping) {
        AwsInfrastructureMapping awsInfrastructureMapping = (AwsInfrastructureMapping) infraMapping;
        msg.append("\n\nCheck whether ");
        if (awsInfrastructureMapping.isProvisionInstances()) {
          msg.append("your Auto Scale group [")
              .append(awsInfrastructureMapping.getAutoScalingGroupName())
              .append("] capacity has changed. ");
        } else {
          msg.append("the filters specified in your service infrastructure are correct. ");
        }
      }
      errorMessage = msg.toString();
    } else if (featureFlagService.isNotEnabled(DEPLOY_TO_INLINE_HOSTS, infraMapping.getAccountId())
        && serviceInstances.size() > totalAvailableInstances) {
      errorMessage =
          "Too many nodes selected. Did you change service infrastructure without updating Select Nodes in the workflow?";
    } else if (serviceInstances.size() > DEFAULT_CONCURRENT_EXECUTION_INSTANCE_LIMIT) {
      Account account = accountService.get(requireNonNull(context.getApp()).getAccountId());
      if (account == null
          || (account.getLicenseInfo() != null && isNotEmpty(account.getLicenseInfo().getAccountType())
              && AccountType.COMMUNITY.equals(account.getLicenseInfo().getAccountType()))) {
        errorMessage = "The license for this account does not allow more than "
            + DEFAULT_CONCURRENT_EXECUTION_INSTANCE_LIMIT
            + " concurrent instance deployments. Please contact Harness Support.";
      }
    }
    return errorMessage;
  }

  @VisibleForTesting
  int renderInstanceCount(ExecutionContext context) {
    int count = 0;
    if (isNotEmpty(instanceCount)) {
      try {
        count = Integer.parseInt(context.renderExpression(instanceCount));
        if (count <= 0) {
          throw new InvalidRequestException("Count or percent must be specified", WingsException.USER);
        } else if (instanceUnitType == PERCENTAGE && count > 100) {
          throw new InvalidRequestException("Percent may not be greater than 100", WingsException.USER);
        }
      } catch (NumberFormatException e) {
        throw new InvalidRequestException(
            format("Unable to render instance count using the expression: [%s]", instanceCount), e,
            WingsException.USER);
      }
    }
    return count;
  }

  private List<ServiceInstance> excludeHostsWithTheSameArtifactDeployed(ExecutionContext context, String appId,
      String serviceId, String inframappingId, List<ServiceInstance> serviceInstances) {
    if (isEmpty(serviceInstances)) {
      return serviceInstances;
    }
    // TODO: ASR: change this.
    Artifact artifact = findArtifact(context, serviceId);
    if (artifact == null) {
      return serviceInstances;
    }
    PageRequest<Instance> pageRequest = aPageRequest()
                                            .withLimit(PageRequest.UNLIMITED)
                                            .addFilter("appId", EQ, appId)
                                            .addFilter("serviceId", EQ, serviceId)
                                            .addFilter("infraMappingId", EQ, inframappingId)
                                            .addFilter("lastArtifactStreamId", EQ, artifact.getArtifactStreamId())
                                            .addFilter("lastArtifactSourceName", EQ, artifact.getArtifactSourceName())
                                            .addFilter("lastArtifactBuildNum", EQ, artifact.getBuildNo())
                                            .build();
    List<Instance> instances = instanceService.list(pageRequest).getResponse();
    List<String> hostNameList =
        instances.stream().map(instance -> instance.getHostInstanceKey().getHostName()).collect(toList());
    return serviceInstances.stream()
        .filter(serviceInstance -> !hostNameList.contains(serviceInstance.getHostName()))
        .collect(toList());
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> invalidFieldMessages = new HashMap<>();
    if (specificHosts && isEmpty(hostNames)) {
      invalidFieldMessages.put(WorkflowServiceHelper.SELECT_NODE_NAME, "Hostnames must be specified");
    }
    return invalidFieldMessages;
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private Artifact findArtifact(ExecutionContext context, String serviceId) {
    ContextElement instanceElement = context.getContextElement(ContextElementType.INSTANCE);
    if (instanceElement != null) {
      ServiceInstanceArtifactParam serviceArtifactElement = context.getContextElement(
          ContextElementType.PARAM, ServiceInstanceArtifactParam.SERVICE_INSTANCE_ARTIFACT_PARAMS);
      if (serviceArtifactElement != null) {
        String artifactId = serviceArtifactElement.getInstanceArtifactMap().get(instanceElement.getUuid());
        if (artifactId != null) {
          return artifactService.get(artifactId);
        }
      }
    }

    return ((DeploymentExecutionContext) context).getArtifactForService(serviceId);
  }

  public List<String> getHostNames() {
    return hostNames;
  }

  public void setHostNames(List<String> hostNames) {
    this.hostNames = hostNames;
  }

  public String getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(String instanceCount) {
    this.instanceCount = instanceCount;
  }

  public InstanceUnitType getInstanceUnitType() {
    return instanceUnitType;
  }

  public void setInstanceUnitType(InstanceUnitType instanceUnitType) {
    this.instanceUnitType = instanceUnitType;
  }

  public boolean isSpecificHosts() {
    return specificHosts;
  }

  public void setSpecificHosts(boolean specificHosts) {
    this.specificHosts = specificHosts;
  }

  public boolean getExcludeSelectedHostsFromFuturePhases() {
    return excludeSelectedHostsFromFuturePhases;
  }

  public void setExcludeSelectedHostsFromFuturePhases(Boolean excludeSelectedHostsFromFuturePhases) {
    this.excludeSelectedHostsFromFuturePhases = excludeSelectedHostsFromFuturePhases;
  }
}
