/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * The type Service instance ids param.
 */
@Value
@Builder
@TargetModule(_957_CG_BEANS)
public class InstanceElementListParam implements ContextElement {
  public static final String INSTANCE_LIST_PARAMS = "INSTANCE_LIST_PARAMS";

  private List<InstanceElement> instanceElements;
  private List<PcfInstanceElement> pcfInstanceElements;
  private List<PcfInstanceElement> pcfOldInstanceElements;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.PARAM;
  }

  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public String getName() {
    return INSTANCE_LIST_PARAMS;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }
}
