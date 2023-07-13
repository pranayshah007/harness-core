/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm.dlite;

import io.harness.delegate.beans.ci.CICleanupTaskParams;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DliteVmCleanupTaskParams implements CICleanupTaskParams {
  @JsonProperty("pool_id") private String poolId;
  @JsonProperty("stage_runtime_id") private String stageRuntimeId;
  @JsonProperty("log_key") private String logKey; // key to store lite engine logs

  @Builder.Default private static final CICleanupTaskParams.Type type = CICleanupTaskParams.Type.DLITE_VM;

  @Override
  public CICleanupTaskParams.Type getType() {
    return type;
  }
}
