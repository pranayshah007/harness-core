/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;
import io.harness.context.ContextElementType;

import software.wings.beans.container.AwsAutoScalarConfig;
import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Created by rishi on 4/11/17.
 */
@Data
@Builder
@JsonTypeName("containerRollbackRequestElement")
@TargetModule(_957_CG_BEANS)
public class ContainerRollbackRequestElement implements ContextElement, SweepingOutput {
  public static final String CONTAINER_ROLLBACK_REQUEST_PARAM = "CONTAINER_ROLLBACK_REQUEST_PARAM";

  private List<ContainerServiceData> newInstanceData;
  private List<ContainerServiceData> oldInstanceData;
  private String namespace;
  private String controllerNamePrefix;
  private String previousEcsServiceSnapshotJson;
  private List<AwsAutoScalarConfig> previousAwsAutoScalarConfigs;
  private String ecsServiceArn;
  private String releaseName;

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
    return CONTAINER_ROLLBACK_REQUEST_PARAM;
  }

  @Override
  public ContextElement cloneMin() {
    return this;
  }

  @Override
  public String getType() {
    return "containerRollbackRequestElement";
  }
}
