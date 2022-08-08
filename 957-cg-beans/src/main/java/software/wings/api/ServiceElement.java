/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

/**
 * The Class ServiceElement.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@TargetModule(_957_CG_BEANS)
@OwnedBy(CDC)
public class ServiceElement implements ContextElement {
  private String uuid;
  private String name;
  private String description;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.SERVICE;
  }

  @Override
  public ContextElement cloneMin() {
    return ServiceElement.builder().uuid(uuid).name(name).description(description).build();
  }
}
