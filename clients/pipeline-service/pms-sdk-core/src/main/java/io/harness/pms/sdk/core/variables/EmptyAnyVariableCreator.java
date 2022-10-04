/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import static io.harness.pms.plan.creation.PlanCreatorUtils.ANY_TYPE;

import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;

/**
 * An empty variable creator without any additional rules.
 *
 * Create the map of supported types to each element received in constructor and the keyword {@value
 * io.harness.pms.plan.creation.PlanCreatorUtils#ANY_TYPE} as value.
 */
public class EmptyAnyVariableCreator implements VariableCreator<YamlField> {
  private final Set<String> identifiers;

  /**
   * @param identifiers cannot be null or empty
   */
  public EmptyAnyVariableCreator(Set<String> identifiers) {
    if (CollectionUtils.isEmpty(identifiers)) {
      throw new IllegalArgumentException("Type list cannot be null or empty");
    }
    this.identifiers = identifiers;
  }

  @Override
  public VariableCreationResponse createVariablesForField(VariableCreationContext ctx, YamlField field) {
    return VariableCreationResponse.builder().build();
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    identifiers.forEach(type -> supportedTypes.put(type, Collections.singleton(ANY_TYPE)));
    return supportedTypes;
  }
}
