/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.api.ForkElement;
import software.wings.beans.LoopEnvResumeStateParams;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstanceHelper;
import software.wings.sm.StateType;
import software.wings.sm.resume.ResumeStateUtils;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Attributes(title = "EnvLoopResume")
@Slf4j
@FieldNameConstants(innerTypeName = "EnvLoopResumeStateKeys")
public class EnvLoopResumeState extends State {
  // This is guaranteed to contain EnvStateExecutionData.
  @Setter @SchemaIgnore private String prevStateExecutionId;
  @Setter @SchemaIgnore private String prevPipelineExecutionId;
  @Setter @SchemaIgnore private Map<String, String> workflowExecutionIdWithStateExecutionIds;

  @Transient @Inject private ResumeStateUtils resumeStateUtils;
  @Transient @Inject private StateExecutionInstanceHelper stateExecutionInstanceHelper;

  public EnvLoopResumeState(String name) {
    super(name, StateType.ENV_LOOP_RESUME_STATE.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    // should spawn two ENV_RESUME_STATE. Once for each workflowExecutionId.
    String appId = context.getAppId();
    String currPipelineExecutionId = resumeStateUtils.fetchPipelineExecutionId(context);
    notNullCheck("Pipeline execution is null in EnvResumeState", currPipelineExecutionId);
    resumeStateUtils.copyPipelineStageOutputs(appId, prevPipelineExecutionId, prevStateExecutionId, null,
        currPipelineExecutionId, context.getStateExecutionInstanceId());
    ExecutionResponseBuilder executionResponseBuilder =
        resumeStateUtils.prepareExecutionResponseBuilder(context, prevStateExecutionId);
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    StateExecutionInstance stateExecutionInstance = executionContext.getStateExecutionInstance();

    int i = 1;
    List<String> correlationIds = new ArrayList<>();

    for (Map.Entry<String, String> entry : workflowExecutionIdWithStateExecutionIds.entrySet()) {
      String state = getName() + "_" + i;
      ForkElement element = ForkElement.builder().stateName(state).parentId(stateExecutionInstance.getUuid()).build();
      StateExecutionInstance childStateExecutionInstance = stateExecutionInstanceHelper.clone(stateExecutionInstance);
      childStateExecutionInstance.setStateParams(null);

      childStateExecutionInstance.setContextElement(element);
      childStateExecutionInstance.setDisplayName(state);
      childStateExecutionInstance.setStateName(state);
      childStateExecutionInstance.setParentLoopedState(true);
      childStateExecutionInstance.setLoopedStateParams(getLoopStateParams(entry.getKey(), entry.getValue(), state));
      childStateExecutionInstance.setStateType(StateType.ENV_RESUME_STATE.getName());
      childStateExecutionInstance.setNotifyId(element.getUuid());
      executionResponseBuilder.stateExecutionInstance(childStateExecutionInstance);
      correlationIds.add(element.getUuid());
      i++;
    }

    return executionResponseBuilder.async(true).correlationIds(correlationIds).build();
  }

  private LoopEnvResumeStateParams getLoopStateParams(String prevWorkflowId, String prevStateExecutionId, String name) {
    return LoopEnvResumeStateParams.builder()
        .prevPipelineExecutionId(prevPipelineExecutionId)
        .prevStateExecutionId(prevStateExecutionId)
        .prevWorkflowExecutionIds(Collections.singletonList(prevWorkflowId))
        .stepName(name)
        .build();
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // Not doing anything on abort.
  }

  @SchemaIgnore
  @Override
  public Integer getTimeoutMillis() {
    Integer timeout = super.getTimeoutMillis();
    return timeout == null ? ResumeStateUtils.RESUME_STATE_TIMEOUT_MILLIS : timeout;
  }
}
