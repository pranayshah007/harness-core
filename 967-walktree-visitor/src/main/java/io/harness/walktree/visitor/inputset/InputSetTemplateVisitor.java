/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.inputset;

import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.reflection.ReflectionUtils;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitorFieldRegistry;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;
import io.harness.walktree.visitor.DummyVisitableElement;
import io.harness.walktree.visitor.SimpleVisitor;
import io.harness.walktree.visitor.utilities.VisitorDummyElementUtils;
import io.harness.walktree.visitor.utilities.VisitorReflectionUtils;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InputSetTemplateVisitor extends SimpleVisitor<DummyVisitableElement> {
  @Inject private VisitorFieldRegistry visitorFieldRegistry;
  @Getter private Object currentObject;

  private boolean keepRuntimeInput; // when true, ${input} is kept. when false, the rest is kept

  private boolean isEntityChanged;

  public InputSetTemplateVisitor(Injector injector, boolean keepRuntimeInput) {
    super(injector);
    this.keepRuntimeInput = keepRuntimeInput;
    this.isEntityChanged = false;
  }

  public boolean isEntityChanged() {
    return isEntityChanged;
  }

  /*
    Sets up a dummy element and populates those parameter field entries which are input at this node
    if keepRuntimeInput = false, only those which are not runtime input expression are stored
     */
  @Override
  public VisitElementResult visitElement(Object currentElement) {
    Object dummyElement = getNewDummyObject(currentElement);

    boolean hasInput = shouldRetainCurrentField(currentElement, dummyElement, keepRuntimeInput);
    VisitorDummyElementUtils.addToDummyElementMapUsingPredicate(
        getElementToDummyElementMap(), currentElement, dummyElement, object -> hasInput);
    return VisitElementResult.CONTINUE;
  }

  /*
  Finds parameter field elements which are runtime input expressions and sets them into the dummy element. Returns
  whether an runtime input expression was found or not if keepRuntimeInput = false, only if non runtime input expression
  fields were found, true is returned
   */
  boolean shouldRetainCurrentField(Object currentElement, Object dummyElement, boolean keepRuntimeInput) {
    boolean isCurrentElementChanged = false;
    List<Field> currentElementFields = ReflectionUtils.getAllDeclaredAndInheritedFields(currentElement.getClass());
    for (Field currentElementField : currentElementFields) {
      Object fieldValue = ReflectionUtils.getFieldValue(currentElement, currentElementField);
      if (isFieldNull(fieldValue)) {
        continue;
      }
      boolean isValueRuntimeInput = isRuntimeInput(fieldValue);
      if (!(isValueRuntimeInput ^ keepRuntimeInput)) {
        isCurrentElementChanged = true;
        try {
          VisitorReflectionUtils.addValueToField(dummyElement, currentElementField, fieldValue);
        } catch (IllegalAccessException e) {
          throw new InvalidRequestException("Field could not be added because " + e.getMessage());
        }
      } else {
        isEntityChanged = true;
      }
    }

    return isCurrentElementChanged;
  }

  /*
  Adds children present in the dummy element map into the dummy element of the current element
  If current element has no input in itself or any of its children, it is not put in the dummy
  element map
  if keepRuntimeInput = false, the dummy element is stored only if it has non input entities
   */
  @Override
  public VisitElementResult postVisitElement(Object currentElement) {
    VisitorDummyElementUtils.addChildrenToCurrentDummyElement(currentElement, getElementToDummyElementMap(), this);

    currentObject = VisitorDummyElementUtils.getDummyElementFromMap(getElementToDummyElementMap(), currentElement);
    return super.postVisitElement(currentElement);
  }

  /*
  checks if a particular value is a runtime input or not
   */
  private boolean isRuntimeInput(Object value) {
    if (VisitorFieldWrapper.class.isAssignableFrom(value.getClass())) {
      VisitorFieldWrapper wrapper = (VisitorFieldWrapper) value;
      VisitableFieldProcessor visitableFieldProcessor = visitorFieldRegistry.obtain(wrapper.getVisitorFieldType());
      if (visitableFieldProcessor.isNull((VisitorFieldWrapper) value)) {
        return false;
      }
      String expressionValue = visitableFieldProcessor.getExpressionFieldValue(wrapper);
      return NGExpressionUtils.matchesInputSetPattern(expressionValue);
    }
    return false;
  }

  private boolean isFieldNull(Object value) {
    if (value == null) {
      return true;
    }
    if (VisitorFieldWrapper.class.isAssignableFrom(value.getClass())) {
      VisitorFieldWrapper wrapper = (VisitorFieldWrapper) value;
      VisitableFieldProcessor visitableFieldProcessor = visitorFieldRegistry.obtain(wrapper.getVisitorFieldType());
      return visitableFieldProcessor.isNull((VisitorFieldWrapper) value);
    }
    return true;
  }
}
