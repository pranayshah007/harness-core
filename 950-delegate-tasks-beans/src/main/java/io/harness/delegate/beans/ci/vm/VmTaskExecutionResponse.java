/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.logging.CommandExecutionStatus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class VmTaskExecutionResponse implements CITaskExecutionResponse {
  @JsonProperty("delegate_meta_info") private DelegateMetaInfo delegateMetaInfo;
  @JsonProperty("error_message") private String errorMessage;
  @JsonProperty("ip_address") private String ipAddress;
  @JsonProperty("output_vars") private Map<String, String> outputVars;
  @JsonProperty("command_execution_status") private CommandExecutionStatus commandExecutionStatus;
  @JsonProperty("service_statuses") private List<VmServiceStatus> serviceStatuses;
  @JsonProperty("artifact") private byte[] artifact;
  @JsonProperty("pool_driver_used") private String poolDriverUsed;

  @Builder.Default private static final CITaskExecutionResponse.Type type = Type.VM;

  @Override
  public CITaskExecutionResponse.Type getType() {
    return type;
  }
}
