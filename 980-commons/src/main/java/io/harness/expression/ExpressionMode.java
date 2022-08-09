/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.expression;
/**
 * THROW_EXCEPTION_IF_UNRESOLVED: Expression engine should throw exception if any expression is not resolved.
 *
 * RETURN_NULL_IF_UNRESOLVED: Expression engine should return null as expression value if that expression
 * is not resolved.
 *
 * RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED: Expression engine should return original expression itself as expression
 * value if that expression is not resolved.
 */

public enum ExpressionMode {
  THROW_EXCEPTION_IF_UNRESOLVED,
  RETURN_NULL_IF_UNRESOLVED,
  RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED
}
