/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName("ServiceHooksMetadataSweepingOutput")
@TypeAlias("serviceHooksMetadataSweepingOutput")
@RecasterAlias("io.harness.cdng.hooks.steps.ServiceHooksMetadataSweepingOutput")
public class ServiceHooksMetadataSweepingOutput implements ExecutionSweepingOutput {
  @NotNull List<ServiceHookWrapper> finalServiceHooks;
}
