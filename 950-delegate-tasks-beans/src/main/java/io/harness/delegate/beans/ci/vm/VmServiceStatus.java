/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VmServiceStatus {
  @JsonProperty("identifier") @NotNull String identifier;
  @JsonProperty("name") String name;
  @JsonProperty("image") String image;
  @JsonProperty("log_key") String logKey;

  public enum Status {
    @JsonProperty("RUNNING") RUNNING,
    @JsonProperty("ERROR") ERROR;
  }
  @JsonProperty("status") Status status;
  @JsonProperty("error_message") String errorMessage;
}
