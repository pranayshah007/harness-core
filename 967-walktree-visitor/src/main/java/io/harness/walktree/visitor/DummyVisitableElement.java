/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor;

import io.harness.walktree.beans.VisitableChildren;

/**
 * This interface is implemented by VisitorHelper to give dummy visitable element.
 */
public interface DummyVisitableElement {
  Object createDummyVisitableElement(Object originalElement);

  /**
   * Used to handle objects that cannot be handled by the Framework i.e Collection objects and Map.
   * @param object
   * @param visitor
   * @param visitableChildren
   */
  default void handleComplexVisitableChildren(
      Object object, SimpleVisitor visitor, VisitableChildren visitableChildren) {
    // Override this if you have list or map objects in your class.
  }
}
