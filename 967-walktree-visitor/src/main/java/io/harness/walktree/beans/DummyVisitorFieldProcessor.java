/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.beans;

import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;

public class DummyVisitorFieldProcessor implements VisitableFieldProcessor<DummyVisitorField> {
  @Override
  public String getExpressionFieldValue(DummyVisitorField actualField) {
    return null;
  }

  @Override
  public DummyVisitorField updateCurrentField(DummyVisitorField actualField, DummyVisitorField overrideField) {
    return null;
  }

  @Override
  public DummyVisitorField cloneField(DummyVisitorField actualField) {
    return null;
  }

  @Override
  public String getFieldWithStringValue(DummyVisitorField actualField) {
    return null;
  }

  @Override
  public DummyVisitorField createNewFieldWithStringValue(String stringValue) {
    return DummyVisitorField.builder().value(stringValue).build();
  }

  @Override
  public boolean isNull(DummyVisitorField actualField) {
    return false;
  }
}
