package io.harness.accesscontrol.acl.conditions;

import static io.harness.exception.WingsException.USER;

import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.exception.AccessDeniedException;
import io.harness.expression.EngineExpressionEvaluator;

import java.util.HashMap;
import java.util.Map;

public class ACLExpressionEvaluator extends EngineExpressionEvaluator {
  private final HashMap<String, Object> resourceMap;

  public ACLExpressionEvaluator(PermissionCheck permissionCheck, Map<String, String> attributes) {
    super(null);
    this.resourceMap = new HashMap<>();
    resourceMap.put("attribute", attributes);
    resourceMap.put("identifier", permissionCheck.getResourceIdentifier());
    resourceMap.put("type", permissionCheck.getResourceType());
  }

  @Override
  protected void initialize() {
    addToContext("resource", new ResourceFunctor(resourceMap));
  }

  public Boolean evaluateExpression(String jexlExpression) {
    Object result = super.evaluateExpression(jexlExpression);
    if (result != null && Boolean.class.isAssignableFrom(result.getClass())) {
      return (Boolean) result;
    }

    StringBuilder errorMsg = new StringBuilder(128);
    if (result == null) {
      errorMsg.append("Expression ").append(jexlExpression).append(" was evaluated to null. Expected type is Boolean");
    } else {
      errorMsg.append("Expression ")
          .append(jexlExpression)
          .append(":  was evaluated to type: ")
          .append(result.getClass())
          .append(". Expected type is Boolean");
    }
    throw new AccessDeniedException(errorMsg.toString(), USER);
  }
}
