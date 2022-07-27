/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.flowcontrol;

import io.harness.annotation.RecasterAlias;
import io.harness.plancreator.flowcontrol.barriers.BarrierInfoConfig;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@TypeAlias("flowControlConfig")
@RecasterAlias("io.harness.plancreator.flowcontrol.FlowControlConfig")
public class FlowControlConfig {
  @Singular List<BarrierInfoConfig> barriers;
}
