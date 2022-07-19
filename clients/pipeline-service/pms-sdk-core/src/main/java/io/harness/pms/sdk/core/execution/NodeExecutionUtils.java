/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ExpressionResolveFunctor;
import io.harness.expression.ResolveObjectResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.NodeExecutionProto;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.EngineExceptionUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterDocumentField;
import io.harness.pms.yaml.ParameterDocumentFieldMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class NodeExecutionUtils {
  public static int retryCount(NodeExecutionProto nodeExecutionProto) {
    if (isRetry(nodeExecutionProto)) {
      return nodeExecutionProto.getRetryIdsList().size();
    }
    return 0;
  }

  private static boolean isRetry(NodeExecutionProto nodeExecutionProto) {
    return !isEmpty(nodeExecutionProto.getRetryIdsList());
  }

  public ExecutableResponse obtainLatestExecutableResponse(NodeExecutionProto proto) {
    List<ExecutableResponse> executableResponses = proto.getExecutableResponsesList();
    if (isEmpty(executableResponses)) {
      return null;
    }
    return executableResponses.get(executableResponses.size() - 1);
  }

  public Map<String, Object> extractObject(String json) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }
    return RecastOrchestrationUtils.fromJson(json);
  }

  public Map<String, Object> extractAndProcessObject(String json) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }
    return (Map<String, Object>) resolveObject(RecastOrchestrationUtils.fromJson(json));
  }

  public Object resolveObject(Object o) {
    if (o == null) {
      return null;
    }

    ExpressionResolveFunctor extractResolveFunctor = new ExtractResolveFunctorImpl();
    return ExpressionEvaluatorUtils.updateExpressions(o, extractResolveFunctor);
  }

  public static FailureInfo constructFailureInfo(Exception ex) {
    return FailureInfo.newBuilder()
        .addAllFailureTypes(EngineExceptionUtils.getOrchestrationFailureTypes(ex))
        .setErrorMessage(ExceptionUtils.getMessage(ex))
        .build();
  }

  public static StepResponseProto constructStepResponse(Exception ex) {
    return StepResponseProto.newBuilder().setStatus(Status.FAILED).setFailureInfo(constructFailureInfo(ex)).build();
  }

  public static class ExtractResolveFunctorImpl implements ExpressionResolveFunctor {
    @Override
    public String processString(String expression) {
      return expression;
    }

    @Override
    public ResolveObjectResponse processObject(Object o) {
      Optional<ParameterDocumentField> docFieldOptional = ParameterDocumentFieldMapper.fromParameterFieldMap(o);
      if (!docFieldOptional.isPresent()) {
        return new ResolveObjectResponse(false, null);
      }

      ParameterDocumentField docField = docFieldOptional.get();
      return new ResolveObjectResponse(true, resolveObject(docField.fetchFinalValue()));
    }
  }
}
