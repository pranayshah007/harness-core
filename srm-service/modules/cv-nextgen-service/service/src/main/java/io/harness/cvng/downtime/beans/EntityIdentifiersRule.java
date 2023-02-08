/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.beans;

import io.harness.cvng.core.entities.MonitoredService.MonitoredServiceKeys;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class EntityIdentifiersRule extends EntitiesRule {
  public List<EntityDetails> entityIdentifiers;

  @Override
  public RuleType getType() {
    return RuleType.IDENTFIERS;
  }
  public boolean isPresent(Map<String, String> entityDetailsMap) {
    if (entityDetailsMap.containsKey(MonitoredServiceKeys.identifier)) {
      return entityIdentifiers.contains(EntityDetails.builder()
                                            .entityRef(entityDetailsMap.get(MonitoredServiceKeys.identifier))
                                            .enabled(true)
                                            .build());
    }
    return false;
  }
}
