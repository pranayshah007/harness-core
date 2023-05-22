/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.configfile.ConfigFileOutcome;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface ExecutionDetails {
  default List<ArtifactOutcome> getArtifactsOutcome() {
    return Collections.emptyList();
  }
  default Map<String, ConfigFileOutcome> getConfigFilesOutcome() {
    return Collections.emptyMap();
  }
  default Map<String, Object> getEnvVariables() {
    return Collections.emptyMap();
  }
  default Map<String, Object> getOutVariables() {
    return Collections.emptyMap();
  }
}
