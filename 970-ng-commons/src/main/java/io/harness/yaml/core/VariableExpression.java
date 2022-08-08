/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.core;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.lang.annotation.ElementType.FIELD;

import io.harness.annotations.dev.OwnedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({FIELD})
@Retention(RetentionPolicy.RUNTIME)
@OwnedBy(PIPELINE)
// Add uuid (hidden field to the object for handling expressions which are not related to object like in
// PipelineInfoConfig for output and extra expressions)
// Example - identifier or null object (excluding String) these expressions are added against Uuid of the parent object,
// thus your object should contains a hidden(because schema should not expose this) uuid field
public @interface VariableExpression {
  // ParameterField is considered as a leaf value, so if you want any other flow for it, define custom policy
  enum IteratePolicy {
    // Normal flow of the objects as defined in the class
    REGULAR,
    // Normal flow of the objects as defined in the class, but fieldName will be used as defined in customFieldName
    REGULAR_WITH_CUSTOM_FIELD
  }
  IteratePolicy policy() default IteratePolicy.REGULAR;

  // AliasExpression which can be used to refer the same field
  String aliasName() default "";

  // To make variables visible on the variables screen
  boolean visible() default true;

  // fieldName to be used instead of reflection fieldName if policy is REGULAR_WITH_CUSTOM_FIELD
  String customFieldName() default "";

  // skipVariableExpression inclusion for all fields inside it if object or leaf value
  boolean skipVariableExpression() default false;

  // if true it considers the given field as leaf value and only adds variable upto that field only. Handles even
  // ParameterField as well.
  boolean skipInnerObjectTraversal() default false;
}
