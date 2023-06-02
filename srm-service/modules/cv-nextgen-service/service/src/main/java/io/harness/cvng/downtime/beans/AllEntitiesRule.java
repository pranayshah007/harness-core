/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.beans;

import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AllEntitiesRule extends EntitiesRule {
  private static final String ALL_IDENTIFIER = "ALL";
  @Override
  public RuleType getType() {
    return RuleType.ALL;
  }
  public boolean isPresent(Map<String, String> entityDetailsMap) {
    return true;
  }
  @Override
  public Optional<AffectedEntity> getAffectedEntity() {
    return Optional.of(AffectedEntity.builder()
                           .serviceName(ALL_IDENTIFIER)
                           .envName(ALL_IDENTIFIER)
                           .monitoredServiceIdentifier(ALL_IDENTIFIER)
                           .build());
  }
}
