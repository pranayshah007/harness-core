/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.docker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerTaskExecutionResponse implements CITaskExecutionResponse, Serializable, io.harness.tasks.Serializable {
  private DelegateMetaInfo delegateMetaInfo;
  private String errorMessage;
  private String ipAddress;
  private Map<String, String> outputVars;
  private String commandExecutionStatus;
  @Builder.Default private static CITaskExecutionResponse.Type type = Type.DOCKER;

  @Override
  public CITaskExecutionResponse.Type getType() {
    return type;
  }

  public byte[] serialize() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
   return  objectMapper.writeValueAsBytes(this);
  }

  public DockerTaskExecutionResponse deserialize(byte[] data) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper.readValue(data, this.getClass());
  }
}
