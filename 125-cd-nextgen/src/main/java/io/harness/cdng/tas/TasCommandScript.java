/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.harness.annotation.RecasterAlias;
import io.harness.cdng.elastigroup.ElastigroupInstancesSpec;
import io.harness.cdng.elastigroup.ElastigroupInstancesType;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

import javax.validation.constraints.NotNull;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

@Data
@Builder
@NoArgsConstructor
@TypeAlias("tasCommandScript")
@RecasterAlias("io.harness.cdng.tas.TasCommandScript")
public class TasCommandScript {
  @NotNull @JsonProperty("store")
  TasCommandScriptStore store;

  @Builder
  public TasCommandScript(TasCommandScriptStore store) {
    this.store = store;
  }
}
