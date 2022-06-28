/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.ci.beans.entities.CITelemetrySentStatus;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import java.util.Set;

public class CITelemetryStatusSentMorphiaRegistrar implements MorphiaRegistrar {
    @Override
    public void registerClasses(Set<Class> set) {
        set.add(CITelemetrySentStatus.class);
    }

    @Override
    public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
        // no classes to register
    }
}
