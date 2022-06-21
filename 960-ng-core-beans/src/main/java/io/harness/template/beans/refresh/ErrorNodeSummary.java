/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.beans.refresh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
public class ErrorNodeSummary {
  NodeInfo nodeInfo;
  TemplateInfo templateInfo;
  List<ErrorNodeSummary> childrenErrorNodes;

  public void addChildrenErrorNode(ErrorNodeSummary errorNodeSummary) {
    if (childrenErrorNodes == null) {
      childrenErrorNodes = new ArrayList<>();
    }
    childrenErrorNodes.add(errorNodeSummary);
  }
}
