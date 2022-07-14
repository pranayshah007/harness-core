/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.execution.facilitator.DefaultFacilitatorParams;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsCommonsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // keeping ids same
    kryo.register(DefaultFacilitatorParams.class, 2515);

    kryo.register(OrchestrationMap.class, 88401);
    kryo.register(PmsOutcome.class, 88402);
    kryo.register(AbsoluteSdkTimeoutTrackerParameters.class, 88404);
  }
}
