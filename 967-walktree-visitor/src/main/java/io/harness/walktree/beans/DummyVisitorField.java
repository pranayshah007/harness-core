/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.beans;

import io.harness.walktree.registries.visitorfield.VisitorFieldType;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DummyVisitorField implements VisitorFieldWrapper {
  public static final VisitorFieldType VISITOR_FIELD_TYPE = VisitorFieldType.builder().type("DUMMY_FIELD").build();

  String value;
  @Override
  public VisitorFieldType getVisitorFieldType() {
    return VisitorFieldType.builder().type("DUMMY").build();
  }
}
