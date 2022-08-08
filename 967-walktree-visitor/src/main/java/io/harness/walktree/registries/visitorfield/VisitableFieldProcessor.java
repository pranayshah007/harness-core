/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.registries.visitorfield;

public interface VisitableFieldProcessor<T extends VisitorFieldWrapper> {
  /**
   * This function will return string value for walktree impl for handling nodes having properties of custom java
   * classes like ParameterField.
   * @return String value
   */
  String getExpressionFieldValue(T actualField);

  /**
   * This function is used to update current field with required properties of given overrideField.
   * @param overrideField
   * @return T
   */
  T updateCurrentField(T actualField, T overrideField);

  /**
   * This function is used to create clone of field of custom java classes.
   */
  T cloneField(T actualField);

  /**
   * This function returns the error uuid corresponding to the field.
   */
  String getFieldWithStringValue(T actualField);

  /**
   * This functions creates new field with Error field as input.
   */
  T createNewFieldWithStringValue(String stringValue);

  /**
   * This function checks whether field is equivalent to null or not.
   * @param actualField
   * @return
   */
  boolean isNull(T actualField);
}
