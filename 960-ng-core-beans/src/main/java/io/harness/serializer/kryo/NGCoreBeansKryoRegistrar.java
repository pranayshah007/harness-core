/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.ng.core.account.ServiceAccountConfig;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.user.UserMembershipUpdateMechanism;
import io.harness.serializer.KryoRegistrar;
import io.harness.steps.shellscript.ShellType;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(HarnessTeam.PL)
public class NGCoreBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(EnvironmentType.class, 20100);
    kryo.register(DefaultExperience.class, 20101);
    kryo.register(UserMembershipUpdateMechanism.class, 20102);
    kryo.register(AuthenticationMechanism.class, 20103);
    kryo.register(ServiceAccountConfig.class, 20104);
    kryo.register(ShellType.class, 20105);
  }
}
