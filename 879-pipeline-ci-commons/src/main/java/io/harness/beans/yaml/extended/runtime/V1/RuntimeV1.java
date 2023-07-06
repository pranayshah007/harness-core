/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.runtime.V1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true,
    include = JsonTypeInfo.As.EXISTING_PROPERTY, defaultImpl = CloudRuntimeV1.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CloudRuntimeV1.class, name = "cloud")
  , @JsonSubTypes.Type(value = MachineRuntimeV1.class, name = "machine"),
      @JsonSubTypes.Type(value = VMRuntimeV1.class, name = "vm"),
})
public interface RuntimeV1 {
  enum Type {
    @JsonProperty("machine") MACHINE,
    @JsonProperty("cloud") CLOUD,
    @JsonProperty("vm") VM,
    @JsonProperty("kubernetes") KUBERNETES
  }
  Type getType();
}
