/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor;

import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;

public abstract class Visitor {
  @Getter Map<String, Object> contextMap = new ConcurrentHashMap<>();

  /**
   * Invoked for a directory before entries in the Element are visited.
   * @param element
   * @return
   */
  public abstract VisitElementResult preVisitElement(Object element);

  /**
   * Invoked for the current element.
   * @param currentElement
   * @return
   */
  public abstract VisitElementResult visitElement(Object currentElement);

  /**
   * Invoked for an element after children of the element, and all of their
   * descendants, have been visited.
   * @param element
   * @return
   */
  public abstract VisitElementResult postVisitElement(Object element);

  // Entry point to walk the tree
  public VisitElementResult walkElementTree(Object currentElement) {
    // Pre-visit Element
    VisitElementResult prevVisitResult = preVisitElement(currentElement);
    boolean skipToPostVisit = shouldSkipToPostVisit(prevVisitResult);
    if (prevVisitResult == VisitElementResult.TERMINATE) {
      return prevVisitResult;
    }

    // Visit Element
    if (!skipToPostVisit && currentElement instanceof Visitable) {
      VisitElementResult visitElementResult = visitElement(currentElement);
      boolean skipChildren = shouldSkipToPostVisit(visitElementResult);
      if (visitElementResult == VisitElementResult.TERMINATE) {
        return visitElementResult;
      }
      // Visit the children
      if (!skipChildren) {
        Visitable visitable = (Visitable) currentElement;
        if (visitable.getChildrenToWalk() != null) {
          Map<String, List<Object>> childrenToWalk = new HashMap<>();
          for (VisitableChild visitableChild : visitable.getChildrenToWalk().getVisitableChildList()) {
            List<Object> children = childrenToWalk.getOrDefault(visitableChild.getFieldName(), new ArrayList<>());
            if (visitableChild.getValue() != null) {
              children.add(visitableChild.getValue());
            }
            childrenToWalk.put(visitableChild.getFieldName(), children);
          }
          for (Map.Entry<String, List<Object>> fieldNameToChild : childrenToWalk.entrySet()) {
            for (Object object : fieldNameToChild.getValue()) {
              VisitorParentPathUtils.addToParentList(this.getContextMap(), fieldNameToChild.getKey());
              VisitElementResult childVisitResult = walkElementTree(object);
              VisitorParentPathUtils.removeFromParentList(this.getContextMap());
              if (childVisitResult == VisitElementResult.TERMINATE) {
                return childVisitResult;
              }
              if (childVisitResult == VisitElementResult.SKIP_SIBLINGS) {
                break;
              }
            }
          }
        }
      }
    }

    // Post-visit element
    VisitElementResult postVisitResult = postVisitElement(currentElement);
    if (postVisitResult == VisitElementResult.SKIP_SUBTREE) {
      return VisitElementResult.CONTINUE;
    } else {
      return postVisitResult;
    }
  }

  public boolean shouldSkipToPostVisit(VisitElementResult result) {
    return result == VisitElementResult.SKIP_SIBLINGS || result == VisitElementResult.SKIP_SUBTREE;
  }
}
