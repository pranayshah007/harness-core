/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.individualschema;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ObjectNodeWithMetadata {
  private ObjectNode objectNode;
  private boolean isRootNode;
  private String nodeGroupDifferentiator; // It can be any differentiator for the nodeGroup/nodeType when there are
                                          // multiple nodeGroup/nodeType present under different paths. Example
                                          // stepGroupElementConfig would be different in cd and ci sages.
  private String nodeGroup;
  private String nodeType;
}
