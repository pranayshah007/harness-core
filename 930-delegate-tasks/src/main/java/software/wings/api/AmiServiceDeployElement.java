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

import software.wings.beans.InstanceUnitType;
import software.wings.sm.ContextElement;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Created by anubhaw on 12/22/17.
 */
@Data
@Builder
@TargetModule(_957_CG_BEANS)
public class AmiServiceDeployElement implements ContextElement {
  private int instanceCount;
  private InstanceUnitType instanceUnitType;
  private List<ContainerServiceData> newInstanceData = Lists.newArrayList();
  private List<ContainerServiceData> oldInstanceData = Lists.newArrayList();

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.AMI_SERVICE_DEPLOY;
  }

  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }
}
