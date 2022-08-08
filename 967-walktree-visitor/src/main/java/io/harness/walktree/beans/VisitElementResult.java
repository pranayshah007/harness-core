/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.beans;

public enum VisitElementResult {
  /**
   * Continue. When returned from a preVisit
   * method then the entries in the directory should also be visited.
   */
  CONTINUE,
  /**
   * Terminate. PostVisit will also not be invoked.
   */
  TERMINATE,
  /**
   * Continue without visiting the children. If returned from preVisitElement,
   * then current element is also not visited. if returned from visitElement,
   * only postVisit will be invoked and not its children. If returned from
   * postVisit, no-op behaviour, same as CONTINUE
   */
  SKIP_SUBTREE,

  /**
   * Continue without visiting the unvisited siblings of this element.
   * If returned from the preVisitElement
   * method then the entries in the Element are also
   * skipped but the postVisitElement method is invoked.
   * If returned from the postVisitDirectory method, no further siblings
   * are visited
   */
  SKIP_SIBLINGS;
}
