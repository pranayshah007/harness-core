/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans;

import io.harness.tasks.SerializableResponseData;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Builder
@Slf4j
public class SerializedResponseData implements SerializableResponseData, DelegateResponseData {
  byte[] data;
  TaskType taskType;
  @Builder.Default SerializationFormat serializationFormat = SerializationFormat.KRYO;

  @Override
  public byte[] serialize() {
    return this.data;
  }
}
