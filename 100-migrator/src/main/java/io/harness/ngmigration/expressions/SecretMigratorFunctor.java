/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions;

import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;

import java.util.HashMap;
import java.util.Map;

public class SecretMigratorFunctor implements ExpressionFunctor {
  private Map<String, String> nameToIdentifier;
  private CaseFormat caseFormat;

  public SecretMigratorFunctor(Map<String, String> nameToIdentifier, CaseFormat caseFormat) {
    this.caseFormat = caseFormat;
    if (EmptyPredicate.isEmpty(nameToIdentifier)) {
      this.nameToIdentifier = new HashMap<>();
    } else {
      this.nameToIdentifier = nameToIdentifier;
    }
  }

  public Object getValue(String secretName) {
    String secretIdentifier = MigratorUtility.generateIdentifier(secretName, this.caseFormat);
    if (nameToIdentifier.containsKey(secretName)) {
      secretIdentifier = nameToIdentifier.get(secretName);
    }
    return "<+secrets.getValue(\"" + secretIdentifier + "\")>";
  }
}
