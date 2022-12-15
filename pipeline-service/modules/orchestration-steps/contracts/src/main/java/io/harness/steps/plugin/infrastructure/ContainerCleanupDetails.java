/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.plugin.infrastructure;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.mongodb.morphia.annotations.Id;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("ContainerCleanupDetails")
@JsonTypeName("ContainerCleanupDetails")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.plugin.infrastructure.ContainerCleanupDetails")
public class ContainerCleanupDetails implements ExecutionSweepingOutput {
  List<String> cleanUpContainerNames;
  ContainerStepInfra infrastructure;
  String podName;
  public static final String CLEANUP_DETAILS = "podCleanupDetails";
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
}
