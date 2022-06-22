package io.harness.delegate.beans;

import io.harness.tasks.SerializableResponseData;

import lombok.Getter;
import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import lombok.Builder;
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
