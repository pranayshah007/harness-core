/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.data;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In order to check database value for this class, use {@link OrchestrationMapTest#shouldTestDeserialization()}
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationMap extends LinkedHashMap<String, Object> implements Map<String, Object> {
  @Schema(hidden = true) boolean empty;

  public OrchestrationMap() {}

  protected OrchestrationMap(Map<String, Object> map) {
    super(map);
  }

  public static OrchestrationMap parse(String json) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }
    return new OrchestrationMap(RecastOrchestrationUtils.fromJson(json));
  }

  public static OrchestrationMap parse(Map<String, Object> map) {
    if (EmptyPredicate.isEmpty(map)) {
      return null;
    }

    return new OrchestrationMap(map);
  }

  public String toJson() {
    return RecastOrchestrationUtils.toJson(this);
  }
}
