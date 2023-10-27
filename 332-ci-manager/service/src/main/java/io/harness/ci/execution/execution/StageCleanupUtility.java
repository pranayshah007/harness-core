/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.steps.StepUtils.buildAbstractions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.dto.CITaskDetails;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.CICommonConstants;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmCleanupTaskParams;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Strings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class StageCleanupUtility {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private OutcomeService outcomeService;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CITaskDetailsRepository ciTaskDetailsRepository;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private HPersistence persistence;
  private final int WAIT_TIME_IN_SECOND = 30;

  public void submitCleanupRequest(Ambiance ambiance, String stageIdentifier) throws InterruptedException {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams = buildAndfetchCleanUpParameters(ambiance);
    DelegateTaskRequest delegateTaskRequest = getDelegateCleanupTaskRequest(ambiance, accountId, cleanupParams);
    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION,
        CICleanupTaskNotifyCallback.builder().stageExecutionID(ambiance.getStageExecutionId()).build(), taskId);
    log.info("Submitted cleanup request with taskId {} for planExecutionId {}, stage {}", taskId,
        ambiance.getPlanExecutionId(), stageIdentifier);
  }

  @VisibleForTesting
  public Pair<CICleanupTaskParams, StageInfraDetails> buildAndfetchCleanUpParameters(Ambiance ambiance) {
    StageInfraDetails stageInfraDetails;
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    if (!optionalSweepingOutput.isFound()) {
      // At upgrade time, stage infra sweeping output may not be set.
      OptionalSweepingOutput optionalCleanupSweepingOutput = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(CLEANUP_DETAILS));
      if (!optionalCleanupSweepingOutput.isFound()) {
        log.warn("Sweeping Output PodCleanupDetails is not set, unable to do cleanup since pod might not be created");
        throw new CIStageExecutionException("Unable to do cleanup as PodCleanupDetails was not set");
      } else {
        PodCleanupDetails podCleanupDetails = (PodCleanupDetails) optionalCleanupSweepingOutput.getOutput();
        stageInfraDetails = K8StageInfraDetails.builder()
                                .infrastructure(podCleanupDetails.getInfrastructure())
                                .podName(podCleanupDetails.getPodName())
                                .containerNames(podCleanupDetails.getCleanUpContainerNames())
                                .build();
      }
    } else {
      stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();
    }

    CICleanupTaskParams ciCleanupTaskParams;
    StageInfraDetails.Type type = stageInfraDetails.getType();
    if (type == StageInfraDetails.Type.K8) {
      K8StageInfraDetails k8StageInfraDetails = (K8StageInfraDetails) stageInfraDetails;
      ciCleanupTaskParams = buildK8CleanupParameters(k8StageInfraDetails, ambiance);
    } else if (type == StageInfraDetails.Type.VM) {
      VmStageInfraDetails vmStageInfraDetails = (VmStageInfraDetails) stageInfraDetails;
      ciCleanupTaskParams = buildVmCleanupParameters(ambiance, vmStageInfraDetails);
    } else if (stageInfraDetails.getType() == StageInfraDetails.Type.DLITE_VM) {
      DliteVmStageInfraDetails dliteVmStageInfraDetails = (DliteVmStageInfraDetails) stageInfraDetails;
      ciCleanupTaskParams = buildHostedVmCleanupParameters(ambiance, dliteVmStageInfraDetails);
    } else {
      throw new CIStageExecutionException("Unknown infra type");
    }
    return Pair.of(ciCleanupTaskParams, stageInfraDetails);
  }

  private CIK8CleanupTaskParams buildK8CleanupParameters(K8StageInfraDetails k8StageInfraDetails, Ambiance ambiance) {
    Infrastructure infrastructure = k8StageInfraDetails.getInfrastructure();

    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    String clusterConnectorRef;
    String namespace;
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
      clusterConnectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
      namespace = (String) k8sDirectInfraYaml.getSpec().getNamespace().fetchFinalValue();
    } else {
      throw new CIStageExecutionException("Infra type:" + infrastructure.getType().name() + "is not of k8s type");
    }

    final List<String> podNames = new ArrayList<>();
    podNames.add(k8StageInfraDetails.getPodName());

    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, clusterConnectorRef);

    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));

    String liteEngineIp = null;
    if (optionalOutcome.isFound()) {
      LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome =
          (LiteEnginePodDetailsOutcome) optionalOutcome.getOutcome();
      if (liteEnginePodDetailsOutcome != null) {
        liteEngineIp = liteEnginePodDetailsOutcome.getIpAddress();
      }
    }

    return CIK8CleanupTaskParams.builder()
        .k8sConnector(connectorDetails)
        .cleanupContainerNames(k8StageInfraDetails.getContainerNames())
        .namespace(namespace)
        .podNameList(podNames)
        .LiteEnginePort(LITE_ENGINE_PORT)
        .isLocal(ciExecutionServiceConfig.isLocal())
        .LiteEngineIP(liteEngineIp)
        .serviceNameList(new ArrayList<>())
        .build();
  }

  private CIVmCleanupTaskParams buildVmCleanupParameters(Ambiance ambiance, VmStageInfraDetails vmStageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Unable to fetch stage details. Please retry or verify pipeline yaml");
    }

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();

    return CIVmCleanupTaskParams.builder()
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .poolId(vmStageInfraDetails.getPoolId())
        .infraInfo(vmStageInfraDetails.getInfraInfo())
        .build();
  }

  private DliteVmCleanupTaskParams buildHostedVmCleanupParameters(
      Ambiance ambiance, DliteVmStageInfraDetails stageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Unable to fetch stage details. Please retry or verify pipeline yaml");
    }

    String baseLogKey = LogStreamingStepClientFactory.getLogBaseKey(ambiance);

    String liteEngineLogKey = baseLogKey + "/" + CICommonConstants.LITE_ENGINE_LOG_KEY_SUFFIX;

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
    return DliteVmCleanupTaskParams.builder()
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .poolId(stageInfraDetails.getPoolId())
        .logKey(liteEngineLogKey)
        .context(DliteVmCleanupTaskParams.Context.builder()
                     .accountID(AmbianceUtils.getAccountId(ambiance))
                     .orgID(AmbianceUtils.getOrgIdentifier(ambiance))
                     .projectID(AmbianceUtils.getProjectIdentifier(ambiance))
                     .pipelineID(AmbianceUtils.getPipelineIdentifier(ambiance))
                     .runSequence(ambiance.getMetadata().getRunSequence())
                     .build())
        .build();
  }

  private DelegateTaskRequest getDelegateCleanupTaskRequest(Ambiance ambiance, String accountId,
      Pair<CICleanupTaskParams, StageInfraDetails> cleanupParams) throws InterruptedException {
    List<TaskSelector> taskSelectors = fetchDelegateSelector(ambiance);

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    String taskType = "CI_CLEANUP";
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    boolean executeOnHarnessHostedDelegates = false;
    String stageId = ambiance.getStageExecutionId();
    List<String> eligibleToExecuteDelegateIds = new ArrayList<>();

    CICleanupTaskParams ciCleanupTaskParams = cleanupParams.getLeft();
    StageInfraDetails stageInfraDetails = cleanupParams.getRight();
    CICleanupTaskParams.Type type = ciCleanupTaskParams.getType();
    if (type == CICleanupTaskParams.Type.DLITE_VM) {
      DliteVmStageInfraDetails dliteVmStageInfraDetails = (DliteVmStageInfraDetails) stageInfraDetails;
      DliteVmCleanupTaskParams dliteVmCleanupTaskParams = (DliteVmCleanupTaskParams) ciCleanupTaskParams;
      taskType = TaskType.DLITE_CI_VM_CLEANUP_TASK.getDisplayName();
      executeOnHarnessHostedDelegates = true;
      serializationFormat = SerializationFormat.JSON;

      if (dliteVmStageInfraDetails.isDistributed()) {
        taskType = TaskType.DLITE_CI_VM_CLEANUP_TASK_V2.getDisplayName();
        dliteVmCleanupTaskParams.setDistributed(true);
      } else {
        String delegateId = fetchDelegateId(ambiance);
        if (Strings.isNotBlank(delegateId)) {
          eligibleToExecuteDelegateIds.add(delegateId);
          ciTaskDetailsRepository.deleteFirstByStageExecutionId(stageId);
        } else {
          log.warn("Unable to locate delegate ID for stage ID: {}. Cleanup task may be routed to the wrong delegate",
              stageId);
        }
      }
    }
    // Since we use a same class to handle both VM and DOCKER cases due to they share a lot of similarities in
    // processing logic, and we use a CICleanupTaskParams type name `VM` to represent them. Only docker scenario
    // needs additional step to add matching docker delegate id into the eligible to execute delegate id list.
    else if (type == CICleanupTaskParams.Type.VM) {
      if (((CIVmCleanupTaskParams) ciCleanupTaskParams).getInfraInfo() == CIInitializeTaskParams.Type.DOCKER) {
        // TODO: Start using fetchDelegateId once we start emitting & processing the event for Docker as well
        OptionalOutcome optionalOutput = outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
        VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();
        if (vmDetailsOutcome != null && Strings.isNotBlank(vmDetailsOutcome.getDelegateId())) {
          eligibleToExecuteDelegateIds.add(vmDetailsOutcome.getDelegateId());
        }
      }
    }

    return DelegateTaskRequest.builder()
        .accountId(accountId)
        .executeOnHarnessHostedDelegates(executeOnHarnessHostedDelegates)
        .stageId(stageId)
        .eligibleToExecuteDelegateIds(eligibleToExecuteDelegateIds)
        .taskSelectors(taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()))
        .selectors(taskSelectors)
        .taskSetupAbstractions(abstractions)
        .executionTimeout(java.time.Duration.ofSeconds(900))
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .taskParameters(ciCleanupTaskParams)
        .taskDescription("CI cleanup pod task")
        .build();
  }

  private String fetchDelegateId(Ambiance ambiance) throws InterruptedException {
    OptionalOutcome optionalOutput = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
    VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();

    if (vmDetailsOutcome != null && Strings.isNotBlank(vmDetailsOutcome.getDelegateId())) {
      return vmDetailsOutcome.getDelegateId();
    } else {
      String stageId = ambiance.getStageExecutionId();
      log.info("Could not process the delegate ID for stage ID: {} from the init response. Trying to look in the DB",
          stageId);

      long currentTime = System.currentTimeMillis();
      long waitTill = currentTime + WAIT_TIME_IN_SECOND * 1000;

      while (System.currentTimeMillis() < waitTill) {
        Optional<CITaskDetails> taskDetailsOptional = ciTaskDetailsRepository.findFirstByStageExecutionId(stageId);

        if (taskDetailsOptional.isPresent()) {
          CITaskDetails taskDetails = taskDetailsOptional.get();
          if (Strings.isNotBlank(taskDetails.getDelegateId())) {
            log.info("Successfully found delegate ID: {} corresponding to stage ID: {}", taskDetails.getDelegateId(),
                stageId);
            return taskDetails.getDelegateId();
          }
          break;
        } else {
          Thread.sleep(1000);
        }
      }
    }
    return null;
  }

  private List<TaskSelector> fetchDelegateSelector(Ambiance ambiance) {
    return connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
  }
}
