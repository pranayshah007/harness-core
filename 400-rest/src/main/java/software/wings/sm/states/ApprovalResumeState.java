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

import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateType;
import software.wings.sm.resume.ResumeStateUtils;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.inject.Inject;
import dev.morphia.annotations.Transient;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@FieldNameConstants(innerTypeName = "ApprovalResumeStateKeys")
public class ApprovalResumeState extends State {
  // This is guaranteed to contain ApprovalStateExecutionData.
  @Setter @SchemaIgnore private String prevStateExecutionId;
  @Setter @SchemaIgnore private String prevPipelineExecutionId;

  @Transient @Inject private ResumeStateUtils resumeStateUtils;

  public ApprovalResumeState(String name) {
    super(name, StateType.APPROVAL_RESUME.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String appId = context.getAppId();
    String currPipelineExecutionId = resumeStateUtils.fetchPipelineExecutionId(context);
    notNullCheck("Pipeline execution is null in ApprovalResumeState", currPipelineExecutionId);
    resumeStateUtils.copyPipelineStageOutputs(appId, prevPipelineExecutionId, prevStateExecutionId, null,
        currPipelineExecutionId, context.getStateExecutionInstanceId());
    return resumeStateUtils.prepareExecutionResponse(context, prevStateExecutionId);
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
