/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import lombok.Builder;
import lombok.Value;

/**
 * The Class ForkElement.
 */
@Value
@Builder
public class ForkElement implements ContextElement {
  private String parentId;
  private String stateName;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.FORK;
  }
  @Override
  public String getUuid() {
    return parentId + "-fork-" + stateName;
  }

  @Override
  public String getName() {
    return "Fork-" + stateName;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
