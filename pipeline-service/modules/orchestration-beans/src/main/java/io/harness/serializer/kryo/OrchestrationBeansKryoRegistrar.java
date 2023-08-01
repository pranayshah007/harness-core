/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.OutcomeInstance;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.plan.ConsumerConfig;
import io.harness.pms.contracts.plan.JsonExpansionInfo;
import io.harness.pms.contracts.plan.SdkModuleInfo;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.data.output.PmsSweepingOutput;
import io.harness.pms.sdk.PmsSdkInstance;
import io.harness.pms.serializer.kryo.serializers.ConsumerConfigKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.JsonExpansionInfoKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.SdkModuleInfoKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.SdkStepKryoSerializer;
import io.harness.serializer.KryoRegistrar;
import io.harness.timeout.trackers.active.ActiveTimeoutParameters;

import com.esotericsoftware.kryo.Kryo;
import java.time.Duration;

/**
 * We are trying to remain as independent from Kryo as possible.
 * All the classes which get saved inside DelegateResponseData need to be registered as our
 * WaitNotify engine used that.
 */
@OwnedBy(CDC)
public class OrchestrationBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // Add new Classes Here
    kryo.register(Duration.class, 2516);
    kryo.register(OutcomeInstance.class, 2517);

    kryo.register(ActiveTimeoutParameters.class, 2537);

    // Moved from pms-commons
    kryo.register(PmsSweepingOutput.class, 88403);

    // Add new classes here
    kryo.register(Interrupt.class, 87601);
    kryo.register(State.class, 87602);

    kryo.register(PmsSdkInstance.class, 88408);
    kryo.register(ConsumerConfig.class, ConsumerConfigKryoSerializer.getInstance(), 2627);
    kryo.register(SdkModuleInfo.class, SdkModuleInfoKryoSerializer.getInstance(), 2628);
    kryo.register(SdkStep.class, SdkStepKryoSerializer.getInstance(), 2629);
    kryo.register(JsonExpansionInfo.class, JsonExpansionInfoKryoSerializer.getInstance(), 2630);
  }
}
