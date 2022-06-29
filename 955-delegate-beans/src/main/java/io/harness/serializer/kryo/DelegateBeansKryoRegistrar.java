/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.serializer.KryoRegistrar;

import software.wings.beans.container.ContainerDefinition;
import software.wings.beans.container.LogConfiguration;
import software.wings.beans.container.PortMapping;
import software.wings.beans.container.StorageConfiguration;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(DEL)
public class DelegateBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(ContainerDefinition.class, 5162);
    kryo.register(LogConfiguration.class, 5163);
    kryo.register(StorageConfiguration.class, 5164);
    kryo.register(PortMapping.class, 5222);
    kryo.register(LogConfiguration.LogOption.class, 5223);
    kryo.register(DelegateMetaInfo.class, 5372);
    kryo.register(DelegateInstanceStatus.class, 400133);
  }
}
