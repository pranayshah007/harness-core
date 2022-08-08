/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.InterceptorPriority;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.stub.MetadataUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ClientInterceptor} that adds metadata to grpc calls allowing to route grpc calls to the
 * correct destination (grpc metadata is transferred via HTTP/2 headers).
 *
 * Note:
 *  Currently only used by delegate-gateway.
 */
@OwnedBy(HarnessTeam.DEL)
@Slf4j
@InterceptorPriority(40)
public class HarnessRoutingGrpcInterceptor implements ClientInterceptor {
  private static final Metadata.Key<String> HARNESS_ROUTING_KEY =
      Metadata.Key.of("harness-routing", Metadata.ASCII_STRING_MARSHALLER);

  private static final String ROUTING_DESTINATION_MANAGER = "manager";
  private static final String ROUTING_DESTINATION_EVENTS = "events";

  public static final ClientInterceptor MANAGER = new HarnessRoutingGrpcInterceptor(ROUTING_DESTINATION_MANAGER);
  public static final ClientInterceptor EVENTS = new HarnessRoutingGrpcInterceptor(ROUTING_DESTINATION_EVENTS);

  private final ClientInterceptor innerInterceptor;

  private HarnessRoutingGrpcInterceptor(String routingDestination) {
    Metadata metadata = new Metadata();
    metadata.put(HARNESS_ROUTING_KEY, routingDestination);
    this.innerInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
    return this.innerInterceptor.interceptCall(methodDescriptor, callOptions, channel);
  }
}
