/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.serializer.KryoRegistrar;

import software.wings.security.AppFilter;
import software.wings.security.EnvFilter;
import software.wings.security.ExecutableElementsFilter;
import software.wings.security.GenericEntityFilter;
import software.wings.security.PermissionAttribute;
import software.wings.security.UsageRestrictions;
import software.wings.security.WorkflowFilter;

import com.esotericsoftware.kryo.Kryo;

public class RbacCoreKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(UsageRestrictions.class, 5247);
    kryo.register(UsageRestrictions.AppEnvRestriction.class, 5248);
    kryo.register(GenericEntityFilter.class, 5249);
    kryo.register(EnvFilter.class, 5250);
    kryo.register(WorkflowFilter.class, 5251);
    kryo.register(PermissionAttribute.Action.class, 5354);
    kryo.register(PermissionAttribute.PermissionType.class, 5353);
    kryo.register(PermissionAttribute.class, 5352);
    kryo.register(AppFilter.class, 5357);
    kryo.register(ExecutableElementsFilter.class, 5246);
  }
}
