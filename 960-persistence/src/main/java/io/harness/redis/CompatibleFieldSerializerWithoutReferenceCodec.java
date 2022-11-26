/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis;

import io.harness.serializer.ClassResolver;
import io.harness.serializer.HKryo;

import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer;

public class CompatibleFieldSerializerWithoutReferenceCodec extends AbstractRedissonKryoCodec {
  @Override
  protected synchronized HKryo kryo(ClassResolver classResolver) {
    HKryo kryo = new HKryo(classResolver, false, false);
    kryo.setDefaultSerializer(getDefaultSerializer());

    return kryo;
  }

  protected Class<? extends Serializer> getDefaultSerializer() {
    return CompatibleFieldSerializer.class;
  }
}
