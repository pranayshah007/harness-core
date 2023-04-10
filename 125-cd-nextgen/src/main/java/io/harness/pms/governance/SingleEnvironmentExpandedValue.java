/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import io.harness.ng.core.environment.beans.EnvironmentType;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

// ToDo: Yogesh ( add unit test to verify the keys )

@Data
@Builder
@FieldNameConstants(innerTypeName = "keys")
public class SingleEnvironmentExpandedValue {
  String environmentRef;
  String identifier;
  String name;
  String description;
  EnvironmentType type;
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  Map<String, String> tags;
  String color;
  List<InfrastructureExpandedValue> infrastructures;
}
