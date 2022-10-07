/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.task;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.TaskResponseData;
import io.harness.delegate.beans.DelegateStringResponseData;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializerWrapper;
import io.harness.task.converters.ResponseDataConverter;
import io.harness.task.converters.ResponseDataConverterRegistry;
import io.harness.task.service.HTTPTaskResponse;
import io.harness.task.service.JiraTaskResponse;
import io.harness.task.service.TaskStatusData;
import io.harness.task.service.TaskType;
import io.harness.tasks.ResponseData;

import com.esotericsoftware.kryo.Kryo;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.time.Duration;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CI)
public class TaskServiceTestHelper {
  public static final String JIRA_ISSUE_KEY = "issueKey";
  public static final String JIRA_ISSUE_ID = "TEST-123";
  public static final String JIRA_DESCRIPTION = "description";
  public static final String HTTP_RESPONSE_BODY = "200 OK";
  public static final int HTTP_RESPONSE_CODE = 200;

  @Inject
  KryoSerializerWrapper kryoSerializerWrapper;

  public HTTPTaskResponse getHttpTaskResponse() {
    return HTTPTaskResponse.newBuilder()
        .setHttpResponseBody(HTTP_RESPONSE_BODY)
        .setHttpResponseCode(HTTP_RESPONSE_CODE)
        .build();
  }

  public JiraTaskResponse getJiraTaskResponse() {
    return JiraTaskResponse.newBuilder()
        .setDescription(JIRA_DESCRIPTION)
        .setId(JIRA_ISSUE_ID)
        .setKey(JIRA_ISSUE_KEY)
        .build();
  }

  public DummyHTTPResponseData getDummyHTTPResponseData() {
    return DummyHTTPResponseData.builder()
        .httpResponseBody(HTTP_RESPONSE_BODY)
        .httpResponseCode(HTTP_RESPONSE_CODE)
        .build();
  }

  private StepStatusTaskResponseData getStepStatusTaskResponseData() {
    return StepStatusTaskResponseData.builder()
        .stepStatus(
            io.harness.delegate.task.stepstatus.StepStatus.builder()
                .numberOfRetries(1)
                .totalTimeTakenInMillis(Duration.ofSeconds(50).toMillis())
                .output(io.harness.delegate.task.stepstatus.StepMapOutput.builder().output("VAR1", "VALUE1").build())
                .stepExecutionStatus(io.harness.delegate.task.stepstatus.StepExecutionStatus.SUCCESS)
                .build())
        .build();
  }

  public DummyJIRAResponseData getDummyJIRAResponseData() {
    return DummyJIRAResponseData.builder().id(JIRA_ISSUE_ID).key(JIRA_ISSUE_KEY).description(JIRA_DESCRIPTION).build();
  }

  public byte[] getDeflatedHttpResponseData() {
    return kryoSerializerWrapper.asDeflatedBytes(getDummyHTTPResponseData());
  }

  public byte[] getDeflatedJiraResponseData() {
    return kryoSerializerWrapper.asDeflatedBytes(getDummyJIRAResponseData());
  }

  public byte[] getDeflatedStepStatusTaskResponseData() {
    return kryoSerializerWrapper.asDeflatedBytes(getStepStatusTaskResponseData());
  }

  public TaskStatusData getTaskResponseData() {
    return TaskStatusData.newBuilder()
        .setStepStatus(
            io.harness.task.service.StepStatus.newBuilder()
                .setNumRetries(1)
                .setTotalTimeTaken(com.google.protobuf.Duration.newBuilder().setSeconds(50))
                .setStepExecutionStatus(io.harness.task.service.StepExecutionStatus.SUCCESS)
                .setStepOutput(io.harness.task.service.StepMapOutput.newBuilder().putOutput("VAR1", "VALUE1").build())
                .build())
        .build();
  }

  public TaskResponseData getTaskProgressResponseData() {
    return TaskResponseData.newBuilder()
        .setKryoResultsData(ByteString.copyFrom(
            kryoSerializerWrapper.asDeflatedBytes(DelegateStringResponseData.builder().data("Test").build())))
        .build();
  }

  @Data
  @Builder
  public static class DummyHTTPResponseData implements ResponseData {
    private int httpResponseCode;
    private String httpResponseBody;
  }
  @Data
  @Builder
  private static class DummyJIRAResponseData implements ResponseData {
    private String id;
    private String key;
    private String description;
  }

  @Builder
  public static class DummyHTTPResponseDataConverter implements ResponseDataConverter<HTTPTaskResponse> {
    @Override
    public HTTPTaskResponse convert(ResponseData responseData) {
      DummyHTTPResponseData dummyHTTPResponseData = (DummyHTTPResponseData) responseData;
      return HTTPTaskResponse.newBuilder()
          .setHttpResponseCode(dummyHTTPResponseData.getHttpResponseCode())
          .setHttpResponseBody(dummyHTTPResponseData.getHttpResponseBody())
          .build();
    }

    @Override
    public ResponseData convert(HTTPTaskResponse httpTaskResponse) {
      return DummyHTTPResponseData.builder()
          .httpResponseBody(httpTaskResponse.getHttpResponseBody())
          .httpResponseCode(httpTaskResponse.getHttpResponseCode())
          .build();
    }
  }

  @Builder
  public static class DummyJIRAResponseDataConverter implements ResponseDataConverter<JiraTaskResponse> {
    @Override
    public JiraTaskResponse convert(ResponseData responseData) {
      DummyJIRAResponseData dummyJIRAResponseData = (DummyJIRAResponseData) responseData;
      return JiraTaskResponse.newBuilder()
          .setKey(dummyJIRAResponseData.getKey())
          .setId(dummyJIRAResponseData.getId())
          .setDescription(dummyJIRAResponseData.getDescription())
          .build();
    }

    @Override
    public ResponseData convert(JiraTaskResponse jiraTaskResponse) {
      return DummyJIRAResponseData.builder()
          .id(jiraTaskResponse.getId())
          .key(jiraTaskResponse.getKey())
          .description(jiraTaskResponse.getDescription())
          .build();
    }
  }

  public static void registerConverters(ResponseDataConverterRegistry registry) {
    registry.register(TaskType.HTTP, DummyHTTPResponseDataConverter.builder().build());
    registry.register(TaskType.JIRA, DummyJIRAResponseDataConverter.builder().build());
  }

  public static class TaskServiceTestKryoRegistrar implements KryoRegistrar {
    @Override
    public void register(Kryo kryo) {
      int index = 25 * 10000;
      kryo.register(DummyHTTPResponseData.class, ++index);
      kryo.register(DummyJIRAResponseData.class, ++index);
    }
  }

  public static Class<TaskServiceTestKryoRegistrar> getKryoRegistrar() {
    return TaskServiceTestKryoRegistrar.class;
  }
}
