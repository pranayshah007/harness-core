/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.checks;

import io.harness.checks.mixin.AnnotationMixin;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

public class AnnotationStoreInCheck extends AbstractCheck {
  private static final String MSG_KEY = "mongo.entity.storein.annotation.check";

  @Override
  public int[] getDefaultTokens() {
    return new int[] {
        TokenTypes.ANNOTATION,
    };
  }

  @Override
  public int[] getRequiredTokens() {
    return new int[] {
        TokenTypes.ANNOTATION,
    };
  }

  @Override
  public int[] getAcceptableTokens() {
    return getDefaultTokens();
  }

  @Override
  public void visitToken(DetailAST annotation) {
    boolean isMongoEntity = false;
    boolean hasStoreIn = false;

    while (annotation != null && annotation.getType() == TokenTypes.ANNOTATION) {
      final String name = AnnotationMixin.name(annotation);
      if (name.equals("Entity") || name.equals("Document")) {
        isMongoEntity = true;
      }
      if (name.equals("StoreIn")) {
        hasStoreIn = true;
      }
      annotation = annotation.getNextSibling();
    }

    if (isMongoEntity && !hasStoreIn) {
      log(annotation, MSG_KEY);
    }
  }
}
