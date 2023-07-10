/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.dtos;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.metadata.BuildMetadata;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class BuildTriggerOpsData {
  Map<String, Object> triggerSpecMap;
  Map<String, Object> pipelineBuildSpecMap;
  TriggerDetails triggerDetails;
  BuildMetadata buildMetadata;
  List<String> signaturesToLock;
}
