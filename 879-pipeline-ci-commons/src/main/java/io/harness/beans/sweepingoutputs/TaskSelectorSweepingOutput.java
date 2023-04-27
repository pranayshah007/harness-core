/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.TaskSelector;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.validation.Update;

import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CI)
@RecasterAlias("io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput")
public class TaskSelectorSweepingOutput implements ExecutionSweepingOutput {
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore String uuid;
  List<TaskSelector> taskSelectors;
}
