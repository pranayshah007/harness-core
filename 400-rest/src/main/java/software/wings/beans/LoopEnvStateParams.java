/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import software.wings.service.intfc.WorkflowService;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.StateTypeDescriptor;
import software.wings.sm.states.EnvState.EnvStateKeys;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoopEnvStateParams implements LoopParams {
  String pipelineId;
  String pipelineStageElementId;
  int pipelineStageParallelIndex;
  String stageName;
  String disableAssertion;
  boolean disable;
  String workflowId;
  Map<String, String> workflowVariables;
  String stepName;
  boolean isRollback;

  public State getEnvStateInstanceFromParams(WorkflowService workflowService, String appId) {
    Map<String, StateTypeDescriptor> stencilMap = workflowService.stencilMap(appId);

    StateTypeDescriptor stateTypeDesc =
        stencilMap.get(isRollback ? StateType.ENV_ROLLBACK_STATE.getType() : StateType.ENV_STATE.getType());

    State state = stateTypeDesc.newInstance(stepName);
    Map<String, Object> properties = new HashMap<>();

    properties.put(EnvStateKeys.pipelineId, pipelineId);
    properties.put(EnvStateKeys.pipelineStageElementId, pipelineStageElementId);
    properties.put(EnvStateKeys.pipelineStageParallelIndex, pipelineStageParallelIndex);
    properties.put(EnvStateKeys.stageName, stageName);
    properties.put(EnvStateKeys.disableAssertion, disableAssertion);
    properties.put(EnvStateKeys.disable, disable);
    properties.put(EnvStateKeys.workflowId, workflowId);
    properties.put(EnvStateKeys.workflowVariables, workflowVariables);
    state.parseProperties(properties);
    state.resolveProperties();
    return state;
  }
}
