/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import io.harness.delegate.beans.SerializedResponseData;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.beans.SerializationFormat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SerializedResponseDataHelper {
  @Inject KryoSerializer kryoSerializer;
  @Inject ObjectMapper objectMapper;

  public ResponseData deserialize(ResponseData responseData) {
    if (responseData instanceof SerializedResponseData) {
      SerializedResponseData serializedResponseData = (SerializedResponseData) responseData;
      if (serializedResponseData.getSerializationFormat().equals(SerializationFormat.JSON)) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
          return objectMapper.readValue(
              serializedResponseData.serialize(), serializedResponseData.getTaskType().getResponse());
        } catch (Exception e) {
          log.error("Could not deserialize bytes to object", e);
          return null;
        }
      } else {
        return (ResponseData) kryoSerializer.asInflatedObject(serializedResponseData.serialize());
      }
    }
    return responseData;
  }
}
