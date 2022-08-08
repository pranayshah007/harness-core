/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.validation;

import io.harness.walktree.visitor.DummyVisitableElement;

public interface ConfigValidator extends DummyVisitableElement {
  /**
   * used to do object specific validation.
   *
   * The error is stored in visitor.errorMap as uuid -> ValidationErrors
   * The uuid should be present in one of the field.
   *
   * @param object
   * @param visitor
   */
  void validate(Object object, ValidationVisitor visitor);
}
