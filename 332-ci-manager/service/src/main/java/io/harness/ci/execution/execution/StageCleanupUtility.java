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

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
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
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.pod.CICommonConstants;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.CIVmCleanupTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmCleanupTaskParams;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
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

  public List<TaskSelector> fetchDelegateSelector(Ambiance ambiance) {
    return connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
  }

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

  public CIK8CleanupTaskParams buildK8CleanupParameters(K8StageInfraDetails k8StageInfraDetails, Ambiance ambiance) {
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

  public CIVmCleanupTaskParams buildVmCleanupParameters(Ambiance ambiance, VmStageInfraDetails vmStageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();

    return CIVmCleanupTaskParams.builder()
        .stageRuntimeId(stageDetails.getStageRuntimeID())
        .poolId(vmStageInfraDetails.getPoolId())
        .infraInfo(vmStageInfraDetails.getInfraInfo())
        .build();
  }

  public DliteVmCleanupTaskParams buildHostedVmCleanupParameters(
      Ambiance ambiance, DliteVmStageInfraDetails stageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
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
}
