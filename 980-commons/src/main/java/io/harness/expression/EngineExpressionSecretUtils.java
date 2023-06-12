/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionConstants;
import io.harness.expression.common.ExpressionMode;
import io.harness.text.resolver.ExpressionResolver;
import io.harness.text.resolver.StringReplacer;

import lombok.experimental.UtilityClass;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.logging.impl.NoOpLog;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class EngineExpressionSecretUtils {
  @Deprecated
  public Object revertSecrets(Object o) {
    return ExpressionEvaluatorUtils.updateExpressions(
        o, new SecretRevertResolveFunctor(ExpressionMode.RETURN_NULL_IF_UNRESOLVED));
  }

  public Object revertSecrets(Object o, ExpressionMode expressionMode) {
    return ExpressionEvaluatorUtils.updateExpressions(o, new SecretRevertResolveFunctor(expressionMode));
  }

  private static class SecretRevertResolveFunctor implements ExpressionResolveFunctor {
    private final StringReplacer replacer;

    private SecretRevertResolveFunctor(ExpressionMode expressionMode) {
      JexlEngine engine = new JexlBuilder().logger(new NoOpLog()).create();
      this.replacer = new StringReplacer(new SecretRevertResolver(engine, expressionMode), "${", "}");
    }

    @Override
    public String processString(String expression) {
      return replacer.replace(expression);
    }

    @Override
    public boolean supportsNotExpression() {
      return false;
    }
  }

  private static class SecretRevertResolver implements ExpressionResolver {
    private final JexlEngine engine;
    private final JexlContext ctx;
    private final ExpressionMode expressionMode;

    private SecretRevertResolver(JexlEngine engine, ExpressionMode expressionMode) {
      this.engine = engine;
      this.ctx = new MapContext();
      this.ctx.set("ngSecretManager", new SecretRevertFunctor());
      this.expressionMode = expressionMode;
    }

    @Override
    public Object getContextValue(String key) {
      return ctx.get(key);
    }

    @Override
    public String resolveInternal(String expression) {
      try {
        JexlExpression jexlExpression = engine.createExpression(expression);
        Object value = jexlExpression.evaluate(ctx);
        if (value == null && expressionMode == ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED) {
          return ExpressionConstants.EXPR_START + expression + ExpressionConstants.EXPR_END;
        }
        return String.valueOf(value);
      } catch (Exception ex) {
        return createExpression(expression);
      }
    }

    private String createExpression(String expression) {
      return String.format("${%s}", expression);
    }
  }

  public static class SecretRevertFunctor {
    public String obtain(String secretIdentifier, int token) {
      return EngineExpressionEvaluator.createExpression(String.format("secrets.getValue(\"%s\")", secretIdentifier));
    }
  }
}
