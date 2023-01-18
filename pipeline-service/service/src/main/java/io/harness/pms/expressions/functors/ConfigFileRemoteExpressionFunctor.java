/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.CONFIG_FILE_FUNCTOR;
import static io.harness.NGCommonEntityConstants.CONFIG_FILE_FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.CONFIG_FILE_FUNCTOR_STRING_METHOD_NAME;
import static io.harness.common.EntityTypeConstants.FILES;
import static io.harness.common.EntityTypeConstants.SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.LateBindingMap;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.expression.ExpressionRequest;
import io.harness.pms.contracts.expression.ExpressionResponse;
import io.harness.pms.contracts.expression.RemoteFunctorServiceGrpc.RemoteFunctorServiceBlockingStub;
import io.harness.pms.sdk.core.execution.expression.ExpressionResultUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.utils.PmsGrpcClientUtils;

import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ConfigFileRemoteExpressionFunctor extends LateBindingMap implements ExpressionFunctor {
  private RemoteFunctorServiceBlockingStub remoteFunctorServiceBlockingStub;
  private Ambiance ambiance;

  public Object getAsString(String ref) {
    List<String> args = Arrays.asList(getReferenceType(ref), CONFIG_FILE_FUNCTOR_STRING_METHOD_NAME, ref);
    return get(args);
  }

  public Object getAsBase64(String ref) {
    List<String> args = Arrays.asList(getReferenceType(ref), CONFIG_FILE_FUNCTOR_BASE64_METHOD_NAME, ref);
    return get(args);
  }

  private String getReferenceType(String ref) {
    if (EmptyPredicate.isEmpty(ref)) {
      throw new InvalidArgumentsException("File or secret reference cannot be null or empty");
    }

    return ref.contains("/") ? FILES : SECRETS;
  }

  private Object get(List<String> args) {
    try {
      ExpressionResponse expressionResponse =
          PmsGrpcClientUtils.retryAndProcessException(remoteFunctorServiceBlockingStub::evaluate,
              ExpressionRequest.newBuilder()
                  .setAmbiance(ambiance)
                  .setFunctorKey(CONFIG_FILE_FUNCTOR)
                  .addAllArgs(args)
                  .build());
      if (expressionResponse.getIsPrimitive()) {
        return ExpressionResultUtils.getPrimitiveResponse(
            expressionResponse.getValue(), expressionResponse.getPrimitiveType());
      }
      return RecastOrchestrationUtils.fromJson(expressionResponse.getValue());
    } catch (ClassNotFoundException e) {
      log.error(e.getMessage());
      throw new InvalidRequestException(e.getMessage(), e);
    } catch (Exception ex) {
      log.error("Could not get object from remote functor for key: " + CONFIG_FILE_FUNCTOR);
      throw ex;
    }
  }
}
