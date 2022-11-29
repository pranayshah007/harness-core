/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.params.logsFilterParams;

import io.swagger.annotations.ApiParam;
import java.time.Instant;
import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@FieldDefaults(level = AccessLevel.PRIVATE)
@SuperBuilder
@Data
@NoArgsConstructor
public class TimeRangeLogsFilter extends LogsFilter {
  @ApiParam(required = true) @NotNull @QueryParam("startTime") long startTime;
  @ApiParam(required = true) @NotNull @QueryParam("endTime") long endTime;

  public Instant getStartTime() {
    return Instant.ofEpochMilli(startTime);
  }

  public Instant getEndTime() {
    return Instant.ofEpochMilli(endTime);
  }
}
