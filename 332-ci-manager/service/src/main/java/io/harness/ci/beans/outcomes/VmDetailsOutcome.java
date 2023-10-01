/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.outcomes;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Id;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("vmDetailsOutcome")
@JsonTypeName("vmDetailsOutcome")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.outcomes.VmDetailsOutcome")
public class VmDetailsOutcome implements Outcome {
  String ipAddress;
  String delegateId;
  String poolDriverUsed;
  public static final String VM_DETAILS_OUTCOME = "vmDetailsOutcome";
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
}
