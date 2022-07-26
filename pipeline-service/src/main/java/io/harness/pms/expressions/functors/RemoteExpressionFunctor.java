/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionFunctor;
import io.harness.expression.LateBindingMap;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.ExpressionRequest;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.sdk.core.execution.expression.ExpressionResultUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.PmsGrpcClientUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class RemoteExpressionFunctor extends LateBindingMap implements ExpressionFunctor {
  private RemoteFunctorServiceBlockingStub remoteFunctorServiceBlockingStub;
  private String functorKey;
  public String value;
  Ambiance ambiance;

  @Override
  public Object get(Object args) {
    try {
      List<String> allArgs;
      // checking if args is string or array of string
      if (args instanceof String) {
        allArgs = Collections.singletonList((String) args);
      } else {
        allArgs = Arrays.asList((String[]) args);
      }
      ExpressionResponse expressionResponse = PmsGrpcClientUtils.retryAndProcessException(
          remoteFunctorServiceBlockingStub::evaluate,
          ExpressionRequest.newBuilder().setAmbiance(ambiance).setFunctorKey(functorKey).addAllArgs(allArgs).build());
      if (expressionResponse.getIsPrimitive()) {
        return ExpressionResultUtils.getPrimitiveResponse(
            expressionResponse.getValue(), expressionResponse.getPrimitiveType());
      }
      return RecastOrchestrationUtils.fromJson(expressionResponse.getValue());
    } catch (ClassNotFoundException e) {
      log.error(e.getMessage());
      throw new InvalidRequestException(e.getMessage(), e);
    } catch (Exception ex) {
      log.error("Could not get object from remote functor for key: " + functorKey);
      throw ex;
    }
  }

  public Object getValue(String... args) {
    return get(args);
  }
}
