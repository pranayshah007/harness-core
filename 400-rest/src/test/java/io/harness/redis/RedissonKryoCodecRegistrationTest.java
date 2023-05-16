/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.reflection.CodeUtils;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;
import io.harness.serializer.ClassResolver;
import io.harness.serializer.HKryo;
import io.harness.serializer.KryoRegistrar;

import software.wings.WingsBaseTest;

import com.esotericsoftware.kryo.serializers.FieldSerializer;
import java.lang.reflect.Constructor;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class RedissonKryoCodecRegistrationTest extends WingsBaseTest {
  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testKryoRegistration() {
    final Set<Class<? extends KryoRegistrar>> kryoRegistrars =
        HarnessReflections.get().getSubTypesOf(KryoRegistrar.class);
    final ClassResolver classResolver = new ClassResolver();
    HKryo kryo = new HKryo(classResolver);
    kryo.setDefaultSerializer(FieldSerializer.class);
    try {
      for (Class<? extends KryoRegistrar> clazz : kryoRegistrars) {
        Constructor<?> constructor = clazz.getConstructor();
        final KryoRegistrar kryoRegistrar = (KryoRegistrar) constructor.newInstance();

        kryo.setCurrentLocation(CodeUtils.location(kryoRegistrar.getClass()));
        kryoRegistrar.register(kryo);
      }
    } catch (Exception ex) {
      throw new GeneralException("Failed initializing kryo", ex);
    }
  }
}
