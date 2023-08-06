/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Builder
@AllArgsConstructor
@Data
@ToString
public class InputFieldMetadata {
  String fieldName;
  String fqnFromParentNode; // eg: stage.spec.xyz, step.spec.connectorRef etc.
  String parentNodeType; // eg: JiraCreate, custom etc.
}
