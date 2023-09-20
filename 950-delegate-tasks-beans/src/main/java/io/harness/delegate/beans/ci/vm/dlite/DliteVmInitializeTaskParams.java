/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.dlite;

import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.SetupVmRequest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DliteVmInitializeTaskParams implements CIInitializeTaskParams {
  @JsonProperty("setup_vm_request") SetupVmRequest setupVmRequest;
  @JsonProperty("services") List<ExecuteStepRequest> services;
  @JsonProperty("distributed") boolean distributed;

  @Builder.Default private static final CIInitializeTaskParams.Type type = Type.DLITE_VM;

  @Override
  public CIInitializeTaskParams.Type getType() {
    return type;
  }
}
