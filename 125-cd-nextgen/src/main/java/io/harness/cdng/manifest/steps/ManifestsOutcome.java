/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@JsonTypeName("ManifestsOutcome")
@TypeAlias("manifestsOutcome")
@RecasterAlias("io.harness.cdng.manifest.steps.ManifestsOutcome")
public class ManifestsOutcome extends HashMap<String, ManifestOutcome> implements Outcome, ExecutionSweepingOutput {
  public ManifestsOutcome() {}

  public ManifestsOutcome(Map<String, ManifestOutcome> map) {
    super(map);
  }
}
