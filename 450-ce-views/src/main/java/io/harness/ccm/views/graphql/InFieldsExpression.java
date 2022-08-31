/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import com.healthmarketscience.common.util.AppendableExt;
import com.healthmarketscience.sqlbuilder.Expression;
import com.healthmarketscience.sqlbuilder.ValidationContext;
import java.io.IOException;
import java.util.List;

public class InFieldsExpression extends Expression {
  private final List<QLCEViewFieldInput> fields;

  public InFieldsExpression(final List<QLCEViewFieldInput> fields) {
    this.fields = fields;
  }

  @Override
  public boolean hasParens() {
    return false;
  }

  @Override
  protected void collectSchemaObjects(final ValidationContext vContext) {
    // No need to collect schema objects
  }

  @Override
  public void appendTo(final AppendableExt app) throws IOException {
    app.append("(");
    for (int i = 0; i < fields.size(); i++) {
      final QLCEViewFieldInput qlceViewFieldInput = fields.get(i);
      app.append(qlceViewFieldInput.getFieldId());
      if (i != fields.size() - 1) {
        app.append(",");
      }
    }
    app.append(")");
  }
}
