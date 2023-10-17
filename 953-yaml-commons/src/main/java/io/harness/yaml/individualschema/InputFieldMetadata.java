/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.individualschema;

import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@ToString
public class InputFieldMetadata {
  String parentNodeType; // eg: JiraCreate, custom etc.
  FQN fqn;

  private static List<String> baseNodeGroups = Arrays.asList("stage", "stages", "step", "steps");

  // Uses current field fqn to calculate fqn from parent node for a sibling field (from same step)
  public String getFqnForSiblingField(String fieldName) {
    String fqnFromParentNode = getFqnFromParentNode();
    return fqnFromParentNode.substring(0, fqnFromParentNode.lastIndexOf('.')) + "." + fieldName;
  }

  public static String getFqnStartingFromParentNode(FQN fqn) {
    String fqnFromParentNode = "";
    for (FQNNode fqnNode : fqn.getFqnList()) {
      if (baseNodeGroups.contains(fqnNode.getKey())) {
        fqnFromParentNode = fqnNode.getKey();
      } else {
        if (fqnNode.getKey() != null) {
          fqnFromParentNode += "." + fqnNode.getKey();
        }
      }
    }
    return fqnFromParentNode;
  }

  public String getFqnFromParentNode() {
    return getFqnStartingFromParentNode(fqn);
  }

  public String getFieldName() {
    return fqn.getFieldName();
  }
}
