/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.validation.OneOfField;

import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
@OneOfField(fields = {"duration", "endTime"})
@RecasterAlias("io.harness.freeze.beans.FreezeWindow")
public class FreezeWindow {
  @NotNull String timeZone;
  @NotNull String startTime;
  String duration;
  String endTime;
  Recurrence recurrence;
}
