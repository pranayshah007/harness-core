/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Stores information for setting up environment for running  CI job
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = K8BuildJobEnvInfo.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8BuildJobEnvInfo.class, name = "K8")
  , @JsonSubTypes.Type(value = VmBuildJobInfo.class, name = "VM")
})
public interface BuildJobEnvInfo {
  enum Type { K8, VM }

  Type getType();
}
