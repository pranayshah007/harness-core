/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.TaskTypeToRequestResponseMapper;
import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
public class SerializedResponseDataHelper {
  @Inject KryoSerializer kryoSerializer;
  @Inject ObjectMapper objectMapper;

  public ResponseData deserialize(ResponseData responseData) {
    if (responseData instanceof SerializedResponseData) {
      SerializedResponseData serializedResponseData = (SerializedResponseData) responseData;
      if (serializedResponseData.getSerializationFormat().equals(SerializationFormat.JSON)) {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Class<? extends DelegateResponseData> responseClass;
        if (!isEmpty(serializedResponseData.getTaskTypeAsString())) {
          TaskType taskType = TaskType.valueOf(serializedResponseData.getTaskTypeAsString());
          responseClass = TaskTypeToRequestResponseMapper.getTaskResponseClass(taskType).orElse(null);
        } else {
          responseClass =
              TaskTypeToRequestResponseMapper.getTaskResponseClass(serializedResponseData.getTaskType()).orElse(null);
        }
        try {
          return objectMapper.readValue(serializedResponseData.serialize(), responseClass);
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
