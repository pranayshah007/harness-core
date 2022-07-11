/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.target;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("targetType")
public enum TargetType {
  @JsonProperty("Pipeline") PIPELINE
  // add more when more targets are decided on
}
