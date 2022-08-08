/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.recaster.proto;

import io.harness.beans.CastedField;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.transformers.RecastTransformer;
import io.harness.transformers.simplevalue.CustomValueTransformer;
import io.harness.utils.RecastReflectionUtils;

import com.google.protobuf.ProtocolMessageEnum;
import java.util.Map;
import lombok.SneakyThrows;

public class ProtoEnumRecastTransformer extends RecastTransformer implements CustomValueTransformer {
  @SneakyThrows
  @Override
  @SuppressWarnings("unchecked")
  public Object decode(Class<?> targetClass, Object fromObject, CastedField castedField) {
    if (fromObject == null) {
      return null;
    }

    Object decodedObject = RecastOrchestrationUtils.getEncodedValue((Map<String, Object>) fromObject);

    if (decodedObject == null) {
      return null;
    }

    String enumName = (String) decodedObject;

    Class<? extends ProtocolMessageEnum> enumClass = (Class<? extends ProtocolMessageEnum>) targetClass;
    return enumClass.getMethod("valueOf", String.class).invoke(null, enumName);
  }

  @SneakyThrows
  @Override
  public Object encode(Object value, CastedField castedField) {
    if (value == null) {
      return null;
    }

    ProtocolMessageEnum protocolMessageEnum = (ProtocolMessageEnum) value;
    return protocolMessageEnum.getValueDescriptor().getName();
  }

  @Override
  public boolean isSupported(final Class<?> c, final CastedField castedField) {
    return RecastReflectionUtils.implementsInterface(c, ProtocolMessageEnum.class);
  }
}
