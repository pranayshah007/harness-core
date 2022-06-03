/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import io.harness.exception.UnsupportedOperationException;

import com.github.rutledgepaulv.qbuilders.nodes.ComparisonNode;
import com.github.rutledgepaulv.qbuilders.visitors.MongoVisitor;
import java.util.Set;
import org.springframework.data.mongodb.core.query.Criteria;

public class ConstrainedMongoVisitor extends MongoVisitor {
  private final Set<String> annotatedFields;

  public ConstrainedMongoVisitor(Set<String> annotatedFields) {
    this.annotatedFields = annotatedFields;
  }

  @Override
  public Criteria visit(ComparisonNode node) {
    String field = node.getField().asKey();
    if (!annotatedFields.contains(field)) {
      throw new UnsupportedOperationException(String.format("Filtering on field %s is not supported", field));
    }
    return super.visit(node);
  }
}
