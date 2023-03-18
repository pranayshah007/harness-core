/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.modules.kryo;

import io.harness.serializer.DelegateTaskRegistrars;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.kryo.ApiServiceBeansKryoRegister;

import com.google.common.collect.ImmutableSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sNgTaskRegistars {
  public static final ImmutableSet<Class<? extends KryoRegistrar>> kryoRegistrars =
      ImmutableSet
          .<Class<? extends KryoRegistrar>>builder()
          // FIXME: use minimum registars
          .addAll(DelegateTaskRegistrars.kryoRegistrars)
          .add(ApiServiceBeansKryoRegister.class)
          .build();
}
