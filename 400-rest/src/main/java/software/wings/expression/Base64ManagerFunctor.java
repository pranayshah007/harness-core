/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.FunctorException;
import io.harness.expression.ExpressionEvaluatorContext;
import io.harness.expression.functors.ExpressionFunctor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._940_SECRET_MANAGER_CLIENT)
public class Base64ManagerFunctor implements ExpressionFunctor {
  private SecretManagerMode mode;
  private Set<String> expressionFunctors;

  public Object encode(String content) {
    Map<String, Object> ctxMap = ExpressionEvaluatorContext.get();
    if (EmptyPredicate.isNotEmpty(ctxMap)) {
      for (Map.Entry<String, Object> entry : ctxMap.entrySet()) {
        if (!expressionFunctors.contains(entry.getKey())) {
          Object value = entry.getValue();

          if (value instanceof Future) {
            Future future = (Future) value;
            try {
              value = future.get();
            } catch (InterruptedException | ExecutionException e) {
              String msg = "Failed to get future value";
              log.error(msg, e);
              throw new FunctorException(msg, e);
            }
          }

          content = content.replaceAll(entry.getKey(), value.toString());
        }
      }
    }
    return EncodingUtils.encodeBase64(content);
  }
}
