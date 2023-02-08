/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.downtime.beans;

import static io.harness.cvng.CVConstants.RULE_TYPE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AllEntitiesRule.class, name = "All")
  , @JsonSubTypes.Type(value = EntityIdentifiersRule.class, name = "Identifiers"),
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = RULE_TYPE, include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
public abstract class EntitiesRule {
  @JsonIgnore public abstract RuleType getType();
  public abstract boolean isPresent(Map<String, String> entityDetailsMap);

  public Optional<AffectedEntity> getAffectedEntity() {
    return Optional.empty();
  }
}
