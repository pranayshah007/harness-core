/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.artifact;

import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ServiceArtifactElement implements ContextElement {
  private String uuid;
  private String name;
  private List<String> serviceIds;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.ARTIFACT;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
