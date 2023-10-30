/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.dlite;

import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DliteVmExecuteStepTaskParams implements CIExecuteStepTaskParams {
  @JsonProperty("execute_step_request") private ExecuteStepRequest executeStepRequest;
  @JsonProperty("context") Context context;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Context {
    @JsonProperty("account_id") String accountID;
    @JsonProperty("org_id") String orgID;
    @JsonProperty("project_id") String projectID;
    @JsonProperty("pipeline_id") String pipelineID;
    @JsonProperty("run_sequence") int runSequence;
  }

  @Builder.Default private static final Type type = Type.DLITE_VM;

  @Override
  public Type getType() {
    return type;
  }
}
