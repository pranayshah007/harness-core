/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import io.harness.annotation.RecasterAlias;

import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@TypeAlias("build")
@RecasterAlias("io.harness.beans.sweepingoutputs.Build")
public class Build {
  String type;
  public Build(String type) {
    this.type = type;
  }
}
