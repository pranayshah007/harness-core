/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.outcomes.LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME;
import static io.harness.beans.outcomes.VmDetailsOutcome.VM_DETAILS_OUTCOME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.ci.CIInitializeTaskParams.Type.DLITE_VM;

import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.IdentifierRef;
import io.harness.beans.dependencies.ServiceDependency;
import io.harness.beans.environment.ServiceDefinitionInfo;
import io.harness.beans.outcomes.DependencyOutcome;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome.VmDetailsOutcomeBuilder;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.ci.buildstate.BuildSetupUtils;
import io.harness.ci.integrationstage.BuildJobEnvInfoBuilder;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.integrationstage.K8InitializeServiceUtils;
import io.harness.ci.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.CIContainerStatus;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.VmServiceStatus;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.TaskExecutableWithRbac;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * This state will setup the build infra e.g. pod or VM.
 */

@Slf4j
@OwnedBy(CI)
public class InitializeTaskStep implements TaskExecutableWithRbac<StepElementParameters, CITaskExecutionResponse> {
  public static final String LE_STATUS_TASK_TYPE = "CI_LE_STATUS";
  public static final Long TASK_BUFFER_TIMEOUT_MILLIS = 30 * 1000L;

  @Inject private BuildSetupUtils buildSetupUtils;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  private static final String DEPENDENCY_OUTCOME = "dependencies";
  public static final StepType STEP_TYPE = InitializeStepInfo.STEP_TYPE;
  @Inject private BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepElementParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      return;
    }

    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();
    List<EntityDetail> connectorsEntityDetails =
        getConnectorIdentifiers(initializeStepInfo, accountIdentifier, projectIdentifier, orgIdentifier);

    if (isNotEmpty(connectorsEntityDetails)) {
      pipelineRbacHelper.checkRuntimePermissions(ambiance, connectorsEntityDetails, true);
    }
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();

    String logPrefix = getLogPrefix(ambiance);

    CIInitializeTaskParams buildSetupTaskParams =
        buildSetupUtils.getBuildSetupTaskParams(initializeStepInfo, ambiance, logPrefix);
    boolean executeOnHarnessHostedDelegates = false;
    boolean emitEvent = false;
    String stageId = ambiance.getStageExecutionId();
    List<TaskSelector> taskSelectors = new ArrayList<>();

    // Secrets are in decrypted format for DLITE_VM type
    if (buildSetupTaskParams.getType() != DLITE_VM) {
      log.info("Created params for build task: {}", buildSetupTaskParams);
    } else {
      HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) initializeStepInfo.getInfrastructure();
      String platformSelector =
          vmInitializeTaskParamsBuilder.getHostedPoolId(hostedVmInfraYaml.getSpec().getPlatform());
      TaskSelector taskSelector = TaskSelector.newBuilder().setSelector(platformSelector).build();
      taskSelectors.add(taskSelector);
      executeOnHarnessHostedDelegates = true;

      emitEvent = true;
    }

    return StepUtils.prepareTaskRequest(ambiance, getTaskData(stepElementParameters, buildSetupTaskParams),
        kryoSerializer, TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), true, null, taskSelectors,
        Scope.PROJECT, EnvironmentType.ALL, executeOnHarnessHostedDelegates, new ArrayList<>(), emitEvent, stageId);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<CITaskExecutionResponse> responseSupplier)
      throws Exception {
    ResponseData responseData = serializedResponseDataHelper.deserialize(responseSupplier.get());
    CITaskExecutionResponse ciTaskExecutionResponse = (CITaskExecutionResponse) responseData;
    if (ciTaskExecutionResponse.getType() == CITaskExecutionResponse.Type.K8) {
      return handleK8TaskResponse(ambiance, stepElementParameters, ciTaskExecutionResponse);
    } else if (ciTaskExecutionResponse.getType() == CITaskExecutionResponse.Type.VM) {
      return handleVmTaskResponse(ciTaskExecutionResponse);
    } else {
      throw new CIStageExecutionException(
          format("Invalid infra type for task response: %s", ciTaskExecutionResponse.getType()));
    }
  }

  public TaskData getTaskData(
      StepElementParameters stepElementParameters, CIInitializeTaskParams buildSetupTaskParams) {
    long timeout =
        Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    String taskType = TaskType.INITIALIZATION_PHASE.getDisplayName();
    if (buildSetupTaskParams.getType() == DLITE_VM) {
      serializationFormat = SerializationFormat.JSON;
      taskType = TaskType.DLITE_CI_VM_INITIALIZE_TASK.getDisplayName();
    }

    return TaskData.builder()
        .async(true)
        .timeout(timeout + TASK_BUFFER_TIMEOUT_MILLIS)
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .parameters(new Object[] {buildSetupTaskParams})
        .build();
  }

  private StepResponse handleK8TaskResponse(
      Ambiance ambiance, StepElementParameters stepElementParameters, CITaskExecutionResponse ciTaskExecutionResponse) {
    K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) ciTaskExecutionResponse;
    InitializeStepInfo initializeStepInfo = (InitializeStepInfo) stepElementParameters.getSpec();

    DependencyOutcome dependencyOutcome =
        getK8DependencyOutcome(ambiance, initializeStepInfo, k8sTaskExecutionResponse.getK8sTaskResponse());
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
        getPodDetailsOutcome(k8sTaskExecutionResponse.getK8sTaskResponse());

    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder().name(DEPENDENCY_OUTCOME).outcome(dependencyOutcome).build();
    if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      log.info(
          "LiteEngineTaskStep pod creation task executed successfully with response [{}]", k8sTaskExecutionResponse);
      if (liteEnginePodDetailsOutcome == null) {
        throw new CIStageExecutionException("Failed to get pod local ipAddress details");
      }
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(stepOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(POD_DETAILS_OUTCOME)
                           .group(StepOutcomeGroup.STAGE.name())
                           .outcome(liteEnginePodDetailsOutcome)
                           .build())
          .build();

    } else {
      log.error("LiteEngineTaskStep execution finished with status [{}] and response [{}]",
          k8sTaskExecutionResponse.getCommandExecutionStatus(), k8sTaskExecutionResponse);

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED).stepOutcome(stepOutcome);
      if (k8sTaskExecutionResponse.getErrorMessage() != null) {
        stepResponseBuilder.failureInfo(
            FailureInfo.newBuilder().setErrorMessage(k8sTaskExecutionResponse.getErrorMessage()).build());
      }
      return stepResponseBuilder.build();
    }
  }

  private StepResponse handleVmTaskResponse(CITaskExecutionResponse ciTaskExecutionResponse) {
    VmTaskExecutionResponse vmTaskExecutionResponse = (VmTaskExecutionResponse) ciTaskExecutionResponse;
    DependencyOutcome dependencyOutcome = getVmDependencyOutcome(vmTaskExecutionResponse);
    StepResponse.StepOutcome stepOutcome =
        StepResponse.StepOutcome.builder().name(DEPENDENCY_OUTCOME).outcome(dependencyOutcome).build();

    if (vmTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      return StepResponse.builder()
          .status(Status.SUCCEEDED)
          .stepOutcome(stepOutcome)
          .stepOutcome(StepResponse.StepOutcome.builder()
                           .name(VM_DETAILS_OUTCOME)
                           .group(StepOutcomeGroup.STAGE.name())
                           .outcome(getVmDetailsOutcome(vmTaskExecutionResponse))
                           .build())
          .build();
    } else {
      log.error("VM initialize step execution finished with status [{}] and response [{}]",
          vmTaskExecutionResponse.getCommandExecutionStatus(), vmTaskExecutionResponse);
      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.FAILED).stepOutcome(stepOutcome);
      if (vmTaskExecutionResponse.getErrorMessage() != null) {
        stepResponseBuilder.failureInfo(
            FailureInfo.newBuilder().setErrorMessage(vmTaskExecutionResponse.getErrorMessage()).build());
      }
      return stepResponseBuilder.build();
    }
  }

  private VmDetailsOutcome getVmDetailsOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    VmDetailsOutcomeBuilder builder = VmDetailsOutcome.builder().ipAddress(vmTaskExecutionResponse.getIpAddress());
    if (vmTaskExecutionResponse.getDelegateMetaInfo() == null
        || isEmpty(vmTaskExecutionResponse.getDelegateMetaInfo().getId())) {
      return builder.build();
    }

    return builder.delegateId(vmTaskExecutionResponse.getDelegateMetaInfo().getId()).build();
  }

  private LiteEnginePodDetailsOutcome getPodDetailsOutcome(CiK8sTaskResponse ciK8sTaskResponse) {
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null) {
      String ip = ciK8sTaskResponse.getPodStatus().getIp();
      String namespace = ciK8sTaskResponse.getPodNamespace();
      return LiteEnginePodDetailsOutcome.builder().ipAddress(ip).namespace(namespace).build();
    }
    return null;
  }

  private DependencyOutcome getK8DependencyOutcome(
      Ambiance ambiance, InitializeStepInfo stepParameters, CiK8sTaskResponse ciK8sTaskResponse) {
    List<ServiceDefinitionInfo> serviceDefinitionInfos =
        k8InitializeServiceUtils.getServiceInfos(stepParameters.getStageElementConfig());
    List<ServiceDependency> serviceDependencyList = new ArrayList<>();
    if (serviceDefinitionInfos == null) {
      return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
    }

    Map<String, CIContainerStatus> containerStatusMap = new HashMap<>();
    if (ciK8sTaskResponse != null && ciK8sTaskResponse.getPodStatus() != null
        && ciK8sTaskResponse.getPodStatus().getCiContainerStatusList() != null) {
      for (CIContainerStatus containerStatus : ciK8sTaskResponse.getPodStatus().getCiContainerStatusList()) {
        containerStatusMap.put(containerStatus.getName(), containerStatus);
      }
    }

    String logPrefix = getLogPrefix(ambiance);
    for (ServiceDefinitionInfo serviceDefinitionInfo : serviceDefinitionInfos) {
      String logKey = format("%s/serviceId:%s", logPrefix, serviceDefinitionInfo.getIdentifier());
      String containerName = serviceDefinitionInfo.getContainerName();
      if (containerStatusMap.containsKey(containerName)) {
        CIContainerStatus containerStatus = containerStatusMap.get(containerName);

        ServiceDependency.Status status = ServiceDependency.Status.SUCCESS;
        if (containerStatus.getStatus() == CIContainerStatus.Status.ERROR) {
          status = ServiceDependency.Status.ERROR;
        }
        serviceDependencyList.add(ServiceDependency.builder()
                                      .identifier(serviceDefinitionInfo.getIdentifier())
                                      .name(serviceDefinitionInfo.getName())
                                      .image(containerStatus.getImage())
                                      .startTime(containerStatus.getStartTime())
                                      .endTime(containerStatus.getEndTime())
                                      .errorMessage(containerStatus.getErrorMsg())
                                      .status(status.getDisplayName())
                                      .logKeys(Collections.singletonList(logKey))
                                      .build());
      } else {
        serviceDependencyList.add(ServiceDependency.builder()
                                      .identifier(serviceDefinitionInfo.getIdentifier())
                                      .name(serviceDefinitionInfo.getName())
                                      .image(serviceDefinitionInfo.getImage())
                                      .errorMessage("Unknown")
                                      .status(ServiceDependency.Status.ERROR.getDisplayName())
                                      .logKeys(Collections.singletonList(logKey))
                                      .build());
      }
    }
    return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
  }

  private DependencyOutcome getVmDependencyOutcome(VmTaskExecutionResponse vmTaskExecutionResponse) {
    List<ServiceDependency> serviceDependencyList = new ArrayList<>();

    List<VmServiceStatus> serviceStatuses = vmTaskExecutionResponse.getServiceStatuses();
    if (isEmpty(serviceStatuses)) {
      return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
    }

    for (VmServiceStatus serviceStatus : serviceStatuses) {
      ServiceDependency.Status status = ServiceDependency.Status.SUCCESS;
      if (serviceStatus.getStatus() == VmServiceStatus.Status.ERROR) {
        status = ServiceDependency.Status.ERROR;
      }
      serviceDependencyList.add(ServiceDependency.builder()
                                    .identifier(serviceStatus.getIdentifier())
                                    .name(serviceStatus.getName())
                                    .image(serviceStatus.getImage())
                                    .errorMessage(serviceStatus.getErrorMessage())
                                    .status(status.getDisplayName())
                                    .logKeys(Collections.singletonList(serviceStatus.getLogKey()))
                                    .build());
    }
    return DependencyOutcome.builder().serviceDependencyList(serviceDependencyList).build();
  }

  private String getLogPrefix(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance, "STAGE");
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  private List<EntityDetail> getConnectorIdentifiers(
      InitializeStepInfo initializeStepInfo, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    List<EntityDetail> entityDetails = new ArrayList<>();
    // Add git clone connector
    if (!initializeStepInfo.isSkipGitClone()) {
      if (initializeStepInfo.getCiCodebase() == null) {
        throw new CIStageExecutionException("Codebase is mandatory with enabled cloneCodebase flag");
      }
      if (isEmpty(initializeStepInfo.getCiCodebase().getConnectorRef().getValue())) {
        throw new CIStageExecutionException("Git connector is mandatory with enabled cloneCodebase flag");
      }

      entityDetails.add(createEntityDetails(initializeStepInfo.getCiCodebase().getConnectorRef().getValue(),
          accountIdentifier, projectIdentifier, orgIdentifier));
    }

    List<String> connectorRefs =
        IntegrationStageUtils.getStageConnectorRefs(initializeStepInfo.getStageElementConfig());
    if (infrastructure.getType() == Infrastructure.Type.VM
        || infrastructure.getType() == Infrastructure.Type.HOSTED_VM) {
      if (!isEmpty(connectorRefs)) {
        entityDetails.addAll(
            connectorRefs.stream()
                .map(connectorIdentifier
                    -> createEntityDetails(connectorIdentifier, accountIdentifier, projectIdentifier, orgIdentifier))
                .collect(Collectors.toList()));
      }
      return entityDetails;
    }

    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_HOSTED) {
      if (((K8sDirectInfraYaml) infrastructure).getSpec() == null) {
        throw new CIStageExecutionException("Input infrastructure can not be empty");
      }
      String infraConnectorRef = ((K8sDirectInfraYaml) infrastructure).getSpec().getConnectorRef().getValue();
      entityDetails.add(createEntityDetails(infraConnectorRef, accountIdentifier, projectIdentifier, orgIdentifier));
    }

    entityDetails.addAll(connectorRefs.stream()
                             .map(connectorIdentifier -> {
                               return createEntityDetails(
                                   connectorIdentifier, accountIdentifier, projectIdentifier, orgIdentifier);
                             })
                             .collect(Collectors.toList()));

    return entityDetails;
  }

  private EntityDetail createEntityDetails(
      String connectorIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(connectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.CONNECTORS).build();
  }
}
