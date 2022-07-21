/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.data.stepdetails;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsStepDetails extends OrchestrationMap {
  public PmsStepDetails(Map<String, Object> map) {
    super(map);
  }

  public static PmsStepDetails parse(String json) {
    if (json == null) {
      return null;
    }
    return new PmsStepDetails(RecastOrchestrationUtils.fromJson(json));
  }

  public static PmsStepDetails parse(Map<String, Object> map) {
    if (map == null) {
      return null;
    }

    return new PmsStepDetails(map);
  }
}
