/**
 *
 */
package software.wings.utils;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlExpression;
import org.apache.commons.jexl3.MapContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Rishi
 */
public class ExpressionEvaluator {
  public static final Pattern wingsVariablePattern = Pattern.compile("\\$\\{[^{}]*\\}");
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private static ExpressionEvaluator instance = new ExpressionEvaluator();

  public static ExpressionEvaluator getInstance() {
    return instance;
  }

  public Object evaluate(String expression, String name, Object value) {
    logger.debug("evaluate request - expression: {}, name: {}, value: {}", expression, name, value);
    JexlEngine jexl = new JexlBuilder().create();
    JexlExpression e = jexl.createExpression(expression);
    JexlContext jc = new MapContext();
    jc.set(name, value);

    Object retValue = e.evaluate(jc);
    logger.debug("evaluate request - return value: {}", retValue);
    return retValue;
  }

  public Object evaluate(String expression, Map<String, Object> context) {
    logger.debug("evaluate request - expression: {}, context: {}", expression, context);
    JexlEngine jexl = new JexlBuilder().create();
    JexlExpression jexp = jexl.createExpression(expression);
    JexlContext jc = new MapContext();
    if (context != null) {
      for (String key : context.keySet()) {
        jc.set(key, context.get(key));
      }
    }
    Object retValue = jexp.evaluate(jc);
    logger.debug("evaluate request - return value: {}", retValue);
    return retValue;
  }

  public Object evaluate(String expression, Map<String, Object> context, String defaultObjectPrefix) {
    expression = normalizeExpression(expression, context, defaultObjectPrefix);
    return evaluate(expression, context);
  }

  private String normalizeExpression(String expression, Map<String, Object> context, String defaultObjectPrefix) {
    Matcher matcher = wingsVariablePattern.matcher(expression);

    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String variable = matcher.group(0);
      logger.debug("wingsVariable found: {}", variable);

      // remove $ and braces(${varName})
      variable = variable.substring(2, variable.length() - 1);

      String topObjectName = variable;
      if (topObjectName.indexOf('.') > 0) {
        topObjectName = topObjectName.substring(0, topObjectName.indexOf('.'));
      }

      if (!context.containsKey(topObjectName)) {
        variable = defaultObjectPrefix + "." + variable;
      }
      matcher.appendReplacement(sb, variable);
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  public String merge(String expression, Map<String, Object> context) {
    return merge(expression, context, null);
  }

  public String merge(String expression, Map<String, Object> context, String defaultObjectPrefix) {
    Matcher matcher = wingsVariablePattern.matcher(expression);

    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String variable = matcher.group(0);
      logger.debug("wingsVariable found: {}", variable);

      // remove $ and braces(${varName})
      variable = variable.substring(2, variable.length() - 1);

      if (defaultObjectPrefix != null) {
        String topObjectName = variable;
        if (topObjectName.indexOf('.') > 0) {
          topObjectName = topObjectName.substring(0, topObjectName.indexOf('.'));
        }
        if (!context.containsKey(topObjectName)) {
          variable = defaultObjectPrefix + "." + variable;
        }
      }

      String evaluatedValue = String.valueOf(evaluate(variable, context));
      matcher.appendReplacement(sb, evaluatedValue);
    }
    matcher.appendTail(sb);

    return sb.toString();
  }
}
