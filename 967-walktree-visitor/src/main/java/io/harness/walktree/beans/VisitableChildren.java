/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.beans;

import io.harness.data.structure.EmptyPredicate;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VisitableChildren {
  @Builder.Default List<VisitableChild> visitableChildList = new ArrayList<>();

  public void add(String fieldName, Object value) {
    visitableChildList.add(VisitableChild.builder().fieldName(fieldName).value(value).build());
  }

  public boolean isEmpty() {
    return EmptyPredicate.isEmpty(visitableChildList);
  }
}
