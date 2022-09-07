/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.stages.stage.StageInfoConfig;

import lombok.experimental.SuperBuilder;

@SuperBuilder
public class TestAbstractStageNode extends AbstractStageNode {
  @Override
  public String getType() {
    return "test";
  }
  @Override
  public StageInfoConfig getStageInfoConfig() {
    return null;
  }
}
