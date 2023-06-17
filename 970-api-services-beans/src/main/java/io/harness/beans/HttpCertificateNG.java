/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Http certificate used with encrypted content.
 */
@Getter
@Setter
@Builder
public class HttpCertificateNG implements NestedAnnotationResolver, DecryptableEntity {
  private String keyStoreType;
  @Expression(ALLOW_SECRETS) String certificate;
  @Expression(ALLOW_SECRETS) String certificateKey;
}
