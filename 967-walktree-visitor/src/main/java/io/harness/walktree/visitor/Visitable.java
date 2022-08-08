/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor;

import io.harness.walktree.beans.VisitableChildren;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This interface should be implemented by each element which wants the element
 * to be visited and whether there are any children to traverse.
 */
public interface Visitable extends WithMetadata {
  /**
   * @return List of objects referring to children on which you want traverse.
   */
  @JsonIgnore
  default VisitableChildren getChildrenToWalk() {
    return null;
  };

  @Override
  default String getMetadata() {
    return null;
  }

  @Override
  default void setMetadata(String metadata) {
    // Nothing to set.
  }
}
