package io.harness.ccm.views.graphql;

import com.healthmarketscience.common.util.AppendableExt;
import com.healthmarketscience.sqlbuilder.Expression;
import com.healthmarketscience.sqlbuilder.SqlObject;
import com.healthmarketscience.sqlbuilder.ValidationContext;
import java.io.IOException;
import java.util.List;

public class CoalesceExpression extends Expression {
  private final SqlObject expression;
  private final List<Object> coalesceExpressionItems;

  public CoalesceExpression(SqlObject expression, List<Object> coalesceExpressionItems) {
    this.expression = expression;
    this.coalesceExpressionItems = coalesceExpressionItems;
  }

  @Override
  public boolean hasParens() {
    return false;
  }

  @Override
  protected void collectSchemaObjects(ValidationContext validationContext) {
    SqlObject.collectSchemaObjects(expression, validationContext);
  }

  @Override
  public void appendTo(AppendableExt app) throws IOException {
    app.append("COALESCE(").append(expression);
    for (Object item : coalesceExpressionItems) {
      app.append(",").append(item);
    }
    app.append(")");
  }
}
