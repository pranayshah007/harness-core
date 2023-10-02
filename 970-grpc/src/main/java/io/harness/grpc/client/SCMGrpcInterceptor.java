/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.grpc.InterceptorPriority;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.MetadataUtils;
import io.opentelemetry.api.trace.Span;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * {@link ClientInterceptor} that adds metadata to grpc calls allowing to route grpc calls to the
 * correct destination (grpc metadata is transferred via HTTP/2 headers).
 *
 */
@OwnedBy(HarnessTeam.CI)
@NoArgsConstructor
@Slf4j
@InterceptorPriority(40)
public class SCMGrpcInterceptor implements ClientInterceptor {
  private static final String taskIdKey = "taskId";
  private static final String traceIdKey = "trace_id";
  private static final String perpetualTaskIdKey = "perpetualTaskId";
  private static final Metadata.Key<String> TASK_ID_KEY = Metadata.Key.of(taskIdKey, Metadata.ASCII_STRING_MARSHALLER);

  public static final ClientInterceptor INTERCEPTOR = new SCMGrpcInterceptor();

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
    Metadata metadata = new Metadata();
    String traceId;
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    // first check the log context
    traceId = getValueFromContextMap(contextMap, taskIdKey, perpetualTaskIdKey, traceIdKey);

    // check if span is not valid then use the trace id
    if (EmptyPredicate.isEmpty(traceId) && !Span.getInvalid().equals(Span.current())) {
      traceId = Span.current().getSpanContext().getTraceId();
    }
    metadata.put(TASK_ID_KEY, traceId);
    return MetadataUtils.newAttachHeadersInterceptor(metadata).interceptCall(methodDescriptor, callOptions, channel);
  }

  private String getValueFromContextMap(Map<String, String> contextMap, String... keys) {
    if (EmptyPredicate.isEmpty(contextMap)) {
      return "";
    }
    for (String key : keys) {
      if (contextMap.containsKey(key)) {
        return contextMap.get(key);
      }
    }
    return "";
  }
}
