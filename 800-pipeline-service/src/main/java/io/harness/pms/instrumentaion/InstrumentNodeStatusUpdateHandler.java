package io.harness.pms.instrumentaion;

import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ERROR_MESSAGES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.EXECUTION_TIME;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.FAILURE_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.LEVEL;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_EXECUTION;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STATUS;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.observers.NodeStatusUpdateObserver;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class InstrumentNodeStatusUpdateHandler implements AsyncInformObserver, NodeStatusUpdateObserver {
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject TelemetryReporter telemetryReporter;
  @Inject AccountService accountService;

  @Override
  public void onNodeStatusUpdate(NodeUpdateInfo nodeUpdateInfo) {
    NodeExecution nodeExecution = nodeUpdateInfo.getNodeExecution();
    Ambiance ambiance = nodeUpdateInfo.getNodeExecution().getAmbiance();
    if ((nodeExecution.getNode().getStepType().getStepCategory() == StepCategory.STAGE
            || nodeExecution.getNode().getStepType().getStepCategory() == StepCategory.STEP)
        && StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      AccountDTO accountDTO = accountService.getAccount(accountId);
      String accountName = accountDTO.getName();
      String email = PipelineInstrumentationUtils.getIdentityFromAmbiance(ambiance);

      HashMap<String, Object> propertiesMap = new HashMap<>();
      propertiesMap.put(ACCOUNT_NAME, accountName);
      propertiesMap.put(EXECUTION_TIME, (nodeUpdateInfo.getUpdatedTs() - nodeExecution.getStartTs()) / 1000);
      propertiesMap.put(LEVEL, nodeExecution.getNode().getStepType().getStepCategory());
      propertiesMap.put(STATUS, nodeExecution.getStatus());
      propertiesMap.put(FAILURE_TYPES, PipelineInstrumentationUtils.getFailureTypesFromNodeExecution(nodeExecution));
      propertiesMap.put(ERROR_MESSAGES, PipelineInstrumentationUtils.getErrorMessagesFromNodeExecution(nodeExecution));

      telemetryReporter.sendTrackEvent(PIPELINE_EXECUTION, email, accountId, propertiesMap,
          Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
          TelemetryOption.builder().sendForCommunity(true).build());
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
