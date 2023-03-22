/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import com.esotericsoftware.kryo.serializers.FieldSerializer.Bind;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public class RemoteMethodReturnValueData implements DelegateResponseData {
  private Object returnValue;
  @Bind(serializer = JavaSerializer.class) private Throwable exception;
}
