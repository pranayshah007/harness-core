/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.sm.ContextElement;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class CloudFormationOutputInfoElement implements CloudFormationElement {
  private Map<String, Object> newStackOutputs;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.CLOUD_FORMATION_PROVISION;
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

  public void mergeOutputs(Map<String, Object> newMap) {
    if (newStackOutputs == null) {
      newStackOutputs = new HashMap<>();
    }
    newStackOutputs.putAll(newMap);
  }
}
