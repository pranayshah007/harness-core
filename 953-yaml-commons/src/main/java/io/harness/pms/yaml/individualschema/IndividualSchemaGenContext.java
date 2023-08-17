/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.individualschema;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(PIPELINE)
public class IndividualSchemaGenContext {
  Set<String> resolvedFqnSet;
  JsonNode rootSchemaNode;
  public void addFqnIntoResolvedFqnMap(String fieldName) {
    resolvedFqnSet.add(fieldName);
  }
}