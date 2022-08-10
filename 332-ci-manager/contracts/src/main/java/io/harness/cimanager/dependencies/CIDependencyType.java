/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.dependencies;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("ciDependencyType")
@RecasterAlias("io.harness.beans.dependencies.CIDependencyType")
public enum CIDependencyType {
  @JsonProperty("Service") SERVICE
}
