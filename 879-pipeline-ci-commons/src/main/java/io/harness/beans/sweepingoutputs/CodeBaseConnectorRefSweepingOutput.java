/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.sweepingoutputs;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
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
@OwnedBy(HarnessTeam.CI)
@TypeAlias(CODE_BASE_CONNECTOR_REF)
@JsonTypeName(CODE_BASE_CONNECTOR_REF)
@RecasterAlias("io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput")
public class CodeBaseConnectorRefSweepingOutput implements ExecutionSweepingOutput {
  String codeBaseConnectorRef;
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore String uuid;
}
