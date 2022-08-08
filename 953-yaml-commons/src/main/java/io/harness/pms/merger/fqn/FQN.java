/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.fqn;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.merger.fqn.FQNNode.NodeType;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class FQN {
  private List<FQNNode> fqnList;

  public static FQN duplicateAndAddNode(FQN base, FQNNode newNode) {
    List<FQNNode> newList = new ArrayList<>(base.getFqnList());
    newList.add(newNode);
    return FQN.builder().fqnList(newList).build();
  }

  public String display() {
    StringBuilder res = new StringBuilder();
    for (FQNNode node : fqnList) {
      if (node.getNodeType() == FQNNode.NodeType.KEY) {
        res.append(node.getKey()).append('.');
      } else if (node.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID) {
        res.append(node.getKey())
            .append('[')
            .append(node.getUuidKey())
            .append(':')
            .append(node.getUuidValue())
            .append("].");
      } else if (node.getNodeType() == FQNNode.NodeType.PARALLEL) {
        res.append("PARALLEL.");
      } else if (node.getNodeType() == FQNNode.NodeType.UUID) {
        res.append('[').append(node.getUuidKey()).append(':').append(node.getUuidValue()).append("].");
      }
    }
    return res.toString();
  }

  public String displayWithoutParallel() {
    StringBuilder res = new StringBuilder();
    for (FQNNode node : fqnList) {
      if (node.getNodeType() == FQNNode.NodeType.KEY) {
        res.append(node.getKey()).append('.');
      } else if (node.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID) {
        res.append(node.getKey())
            .append('[')
            .append(node.getUuidKey())
            .append(':')
            .append(node.getUuidValue())
            .append("].");
      } else if (node.getNodeType() == FQNNode.NodeType.UUID) {
        res.append('[').append(node.getUuidKey()).append(':').append(node.getUuidValue()).append("].");
      }
    }
    return res.toString();
  }

  public String getExpressionFqn() {
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < fqnList.size(); i++) {
      FQNNode currNode = fqnList.get(i);
      if (currNode.getNodeType() == FQNNode.NodeType.KEY
          && !YamlUtils.shouldNotIncludeInQualifiedName(currNode.getKey())) {
        res.append(currNode.getKey()).append('.');
      } else if (currNode.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID) {
        res.append(currNode.getUuidValue()).append('.');
      } else if (currNode.getNodeType() == FQNNode.NodeType.UUID) {
        res.append(currNode.getUuidValue()).append('.');
        if (i < fqnList.size() - 1 && shouldSkipNextNode(currNode, fqnList.get(i + 1))) {
          i++;
        }
      }
    }
    String temp = res.toString();
    return temp.substring(0, temp.length() - 1);
  }

  // This method return the ExpressionFQN without ignoring the ignorableStringForQualifiedName. We need full path in a
  // step/stage/pipeline yaml as expression because we convert the user input yaml to map and use it for expression
  // evaluation.
  public String getExpressionFqnWithoutIgnoring() {
    StringBuilder res = new StringBuilder();
    for (int i = 0; i < fqnList.size(); i++) {
      FQNNode currNode = fqnList.get(i);
      if (currNode.getNodeType() == FQNNode.NodeType.KEY) {
        res.append(currNode.getKey()).append('.');
      } else if (currNode.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID) {
        res.append(currNode.getUuidValue()).append('.');
      } else if (currNode.getNodeType() == FQNNode.NodeType.UUID) {
        res.append(currNode.getUuidValue()).append('.');
        if (i < fqnList.size() - 1 && shouldSkipNextNode(currNode, fqnList.get(i + 1))) {
          i++;
        }
      }
    }
    String temp = res.toString();
    return temp.substring(0, temp.length() - 1);
  }

  /**
   * It skips the next node if the currentNode is derived from name. Performs similar function as YamlUtils#265
   * For example: In variables, it attaches the value node to the fqn `pipeline.variables.variable_name`
   **/
  private boolean shouldSkipNextNode(FQNNode currNode, FQNNode nextNode) {
    if (!currNode.getUuidKey().equals(YAMLFieldNameConstants.NAME)) {
      return false;
    }
    // NOTE: We are explicitly checking for value because the fqn should correspond to the value field in the yaml
    return (nextNode.getNodeType() == FQNNode.NodeType.KEY) && nextNode.getKey().equals("value");
  }

  public boolean contains(FQN baseFQN) {
    String subString = baseFQN.display();
    String string = this.display();
    return string.startsWith(subString);
  }

  public boolean isIdentifierOrVariableName() {
    boolean isVariableName = isVariableName();
    FQNNode lastNode = fqnList.get(fqnList.size() - 1);
    return isVariableName || lastNode.getKey().equals(YAMLFieldNameConstants.IDENTIFIER);
  }

  public boolean isType() {
    FQNNode lastNode = fqnList.get(fqnList.size() - 1);
    return lastNode.getKey().equals(YAMLFieldNameConstants.TYPE);
  }

  public boolean isVariableName() {
    if (fqnList.size() < 2) {
      return false;
    }
    FQNNode lastNode = fqnList.get(fqnList.size() - 1);
    FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);
    return secondLastNode.getNodeType().equals(FQNNode.NodeType.UUID)
        && lastNode.getKey().equals(YAMLFieldNameConstants.NAME);
  }

  public boolean isStageIdentifier() {
    if (fqnList.size() < 2) {
      return false;
    }
    String finalElementFieldName = getFieldName();
    if (!finalElementFieldName.equals(YAMLFieldNameConstants.IDENTIFIER)) {
      return false;
    }
    FQNNode penultimateElement = fqnList.get(fqnList.size() - 2);
    if (penultimateElement.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID) {
      return penultimateElement.getKey().equals(YAMLFieldNameConstants.STAGE);
    }
    return false;
  }

  public String getFieldName() {
    return fqnList.get(fqnList.size() - 1).getKey();
  }

  public String getStageIdentifier() {
    if (fqnList.size() < 2) {
      return null;
    }
    if (fqnList.size() > 3 && fqnList.get(1).getKey().equals(YAMLFieldNameConstants.TEMPLATE)
        && fqnList.get(2).getKey().equals(YAMLFieldNameConstants.TEMPLATE_INPUTS)
        && fqnList.get(3).getKey().equals(YAMLFieldNameConstants.STAGES)) {
      FQNNode stageNode = fqnList.get(4);
      if (stageNode.getNodeType().equals(NodeType.PARALLEL)) {
        stageNode = fqnList.get(5);
      }
      return stageNode.getUuidValue();
    }
    if (!fqnList.get(1).getKey().equals(YAMLFieldNameConstants.STAGES)) {
      return null;
    }
    FQNNode stageNode = fqnList.get(2);
    if (stageNode.getNodeType().equals(NodeType.PARALLEL)) {
      stageNode = fqnList.get(3);
    }
    return stageNode.getUuidValue();
  }
}
