/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks.steps;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.ServiceHookOutcome;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@JsonTypeName("ServiceHooksOutcome")
@TypeAlias("serviceHooksOutcome")
@RecasterAlias("io.harness.cdng.hooks.steps.ServiceHooksOutcome")
public class ServiceHooksOutcome
    extends HashMap<String, ServiceHookOutcome> implements Outcome, ExecutionSweepingOutput {
  public ServiceHooksOutcome() {}

  public ServiceHooksOutcome(Map<String, ServiceHookOutcome> map) {
    super(map);
  }
}
