/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The type Partition element.
 */
@Value
@Builder
public class PartitionElement implements ContextElement {
  private String uuid;
  private String name;
  private List<ContextElement> partitionElements;
  private ContextElementType partitionElementType;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.PARTITION;
  }

  public ContextElementType getPartitionElementType() {
    if (isNotEmpty(partitionElements)) {
      return partitionElements.get(0).getElementType();
    }
    return partitionElementType;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
