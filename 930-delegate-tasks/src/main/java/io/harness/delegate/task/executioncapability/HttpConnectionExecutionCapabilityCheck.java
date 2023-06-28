/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.executioncapability;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.KeyValuePair;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission;
import io.harness.capability.CapabilitySubjectPermission.CapabilitySubjectPermissionBuilder;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.capability.HttpConnectionParameters;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.network.Http;

import java.util.stream.Collectors;

public class HttpConnectionExecutionCapabilityCheck implements CapabilityCheck, ProtoCapabilityCheck {
  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    HttpConnectionExecutionCapability httpConnectionExecutionCapability =
        (HttpConnectionExecutionCapability) delegateCapability;
    boolean valid;
    boolean isNextGen =
        isNotBlank(System.getenv().get("NEXT_GEN")) && Boolean.parseBoolean(System.getenv().get("NEXT_GEN"));
    if (isNextGen) {
      valid = Http.connectableHttpUrlWithoutFollowingRedirect(httpConnectionExecutionCapability.fetchConnectableUrl(),
          httpConnectionExecutionCapability.getHeaders(), httpConnectionExecutionCapability.isIgnoreResponseCode());
    } else {
      if (httpConnectionExecutionCapability.getHeaders() != null) {
        valid = Http.connectableHttpUrlWithHeaders(httpConnectionExecutionCapability.fetchConnectableUrl(),
            httpConnectionExecutionCapability.getHeaders(), httpConnectionExecutionCapability.isIgnoreResponseCode());
      } else {
        if (httpConnectionExecutionCapability.isIgnoreRedirect()) {
          valid = Http.connectableHttpUrlWithoutFollowingRedirect(
              httpConnectionExecutionCapability.fetchConnectableUrl(), httpConnectionExecutionCapability.getHeaders(),
              httpConnectionExecutionCapability.isIgnoreResponseCode());
        } else {
          valid = Http.connectableHttpUrl(httpConnectionExecutionCapability.fetchConnectableUrl(),
              httpConnectionExecutionCapability.isIgnoreResponseCode());
        }
      }
    }
    return CapabilityResponse.builder().delegateCapability(httpConnectionExecutionCapability).validated(valid).build();
  }

  @Override
  public CapabilitySubjectPermission performCapabilityCheckWithProto(CapabilityParameters parameters) {
    CapabilitySubjectPermissionBuilder builder = CapabilitySubjectPermission.builder();
    if (parameters.getCapabilityCase() != CapabilityParameters.CapabilityCase.HTTP_CONNECTION_PARAMETERS) {
      return builder.permissionResult(PermissionResult.DENIED).build();
    }
    HttpConnectionParameters httpConnectionParameters = parameters.getHttpConnectionParameters();
    // Use isNotEmpty since proto3 initializes an emptyList when headers are not added.
    // https://github.com/protocolbuffers/protobuf/blob/v3.12.0/docs/field_presence.md
    if (EmptyPredicate.isNotEmpty(httpConnectionParameters.getHeadersList())) {
      return builder
          .permissionResult(
              Http.connectableHttpUrlWithHeaders(httpConnectionParameters.getUrl(),
                  httpConnectionParameters.getHeadersList()
                      .stream()
                      .map(entry -> KeyValuePair.builder().key(entry.getKey()).value(entry.getValue()).build())
                      .collect(Collectors.toList()),
                  httpConnectionParameters.getIgnoreResponseCode())
                  ? PermissionResult.ALLOWED
                  : PermissionResult.DENIED)
          .build();
    } else {
      return builder
          .permissionResult(Http.connectableHttpUrl(parameters.getHttpConnectionParameters().getUrl(),
                                httpConnectionParameters.getIgnoreResponseCode())
                  ? PermissionResult.ALLOWED
                  : PermissionResult.DENIED)
          .build();
    }
  }
}
