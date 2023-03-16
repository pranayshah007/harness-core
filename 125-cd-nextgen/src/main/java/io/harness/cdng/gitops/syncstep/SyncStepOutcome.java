/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitops.models.Application;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.codehaus.commons.nullanalysis.NotNull;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(GITOPS)
@Value
@Builder
@TypeAlias("SyncStepOutcome")
@JsonTypeName("SyncStepOutcome")
@RecasterAlias("io.harness.cdng.gitops.syncstep.SyncStepOutcome")
public class SyncStepOutcome {
  @NotNull List<Application> applications;
}
