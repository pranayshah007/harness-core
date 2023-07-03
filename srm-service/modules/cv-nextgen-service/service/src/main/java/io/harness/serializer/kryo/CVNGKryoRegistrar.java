/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.cdng.beans.CVNGDeploymentImpactStepParameter;
import io.harness.cvng.cdng.beans.CVNGStepParameter;
import io.harness.cvng.cdng.services.impl.CVNGStep;
import io.harness.cvng.core.entities.SRMTelemetrySentStatus;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(CV)
public class CVNGKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // TODO: should we move CVNGStep and it's logic to a separate module.
    kryo.register(CVNGStep.CVNGResponseData.class, 30000);
    kryo.register(CVNGStepParameter.class, 30001);
    kryo.register(SRMTelemetrySentStatus.class, 30002);
    kryo.register(CVNGDeploymentImpactStepParameter.class, 30003);
  }
}
