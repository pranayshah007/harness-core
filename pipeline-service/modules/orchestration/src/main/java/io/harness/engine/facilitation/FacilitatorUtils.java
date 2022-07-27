/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.facilitation;

import io.harness.pms.execution.facilitator.DefaultFacilitatorParams;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.time.Duration;

public class FacilitatorUtils {
  @Inject private KryoSerializer kryoSerializer;

  public Duration extractWaitDurationFromDefaultParams(byte[] parameters) {
    Duration waitDuration = Duration.ofSeconds(0);
    if (parameters != null && parameters.length > 0) {
      DefaultFacilitatorParams facilitatorParameters = (DefaultFacilitatorParams) kryoSerializer.asObject(parameters);
      waitDuration = facilitatorParameters.getWaitDurationSeconds();
    }
    return waitDuration;
  }
}
