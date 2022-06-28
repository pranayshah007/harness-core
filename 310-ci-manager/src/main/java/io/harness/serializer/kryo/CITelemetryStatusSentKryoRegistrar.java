/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import com.esotericsoftware.kryo.Kryo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.serializer.KryoRegistrar;

@OwnedBy(HarnessTeam.CI)
public class CITelemetryStatusSentKryoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
        kryo.register(CITelemetrySentStatus.class, 110108);
    }
}
