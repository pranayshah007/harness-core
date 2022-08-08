/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.utilities;

import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.reflection.ReflectionUtils;
import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitorFieldRegistry;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;
import io.harness.walktree.visitor.mergeinputset.beans.MergeInputSetErrorResponse;
import io.harness.walktree.visitor.mergeinputset.beans.MergeVisitorInputSetElement;
import io.harness.walktree.visitor.response.VisitorErrorResponseWrapper;
import io.harness.walktree.visitor.response.VisitorErrorResponseWrapper.VisitorErrorResponseWrapperBuilder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class MergeInputSetHelperUtils {
  /**
   * This function addsValue to dummyElement when field is of type VisitableFieldWrapper.class
   */
  public void addFieldValueForVisitableFieldWrapper(Object currentElement, Object dummyElement, Field field,
      Object fieldValue, VisitorFieldRegistry visitorFieldRegistry,
      Map<Object, List<MergeVisitorInputSetElement>> elementToInputSetElementListMap) throws IllegalAccessException {
    VisitorFieldWrapper fieldWrapper = (VisitorFieldWrapper) fieldValue;
    VisitableFieldProcessor fieldProcessor = visitorFieldRegistry.obtain(fieldWrapper.getVisitorFieldType());

    // Null check
    if (fieldProcessor.isNull(fieldWrapper)) {
      return;
    }
    VisitorFieldWrapper clonedField = fieldProcessor.cloneField(fieldWrapper);
    if (isLeafFieldInputSetExpression(fieldWrapper, visitorFieldRegistry)) {
      Object finalInputSetValue = getFinalInputSetValue(
          field, currentElement, elementToInputSetElementListMap, e -> fieldProcessor.isNull((VisitorFieldWrapper) e));
      if (finalInputSetValue != null) {
        fieldProcessor.updateCurrentField(clonedField, (VisitorFieldWrapper) finalInputSetValue);
      }
    }
    VisitorReflectionUtils.addValueToField(dummyElement, field, clonedField);
  }

  /**
   * This function addsValue to dummyElement when field is of type String.class.
   */
  public void addFieldValueForStringField(Object currentElement, Object dummyElement, Field field, Object fieldValue,
      Map<Object, List<MergeVisitorInputSetElement>> elementToInputSetElementListMap) throws IllegalAccessException {
    String stringFieldValue = (String) fieldValue;
    Object finalInputSetValue = fieldValue;
    if (NGExpressionUtils.matchesInputSetPattern(stringFieldValue)) {
      Object appliedInputSetValue =
          getFinalInputSetValue(field, currentElement, elementToInputSetElementListMap, Objects::isNull);
      if (appliedInputSetValue != null) {
        finalInputSetValue = appliedInputSetValue;
      }
    }
    VisitorReflectionUtils.addValueToField(dummyElement, field, finalInputSetValue);
  }

  /**
   * This functions addsValue to dummyElement when field is of type Enum class.
   */
  public void addFieldValueForEnumOrPrimitiveType(Object dummyElement, Field field, Object fieldValue)
      throws IllegalAccessException {
    VisitorReflectionUtils.addValueToField(dummyElement, field, fieldValue);
  }

  /**
   * This functions returns the override value from the inputSetElement corresponding to the current element.
   * @param nullCheckPredicate this checks for null condition for wrapper classes, which are not primitive.
   */
  private Object getFinalInputSetValue(Field field, Object currentElement,
      Map<Object, List<MergeVisitorInputSetElement>> elementToInputSetElementListMap,
      Predicate<Object> nullCheckPredicate) {
    List<MergeVisitorInputSetElement> inputSetList = elementToInputSetElementListMap.get(currentElement);
    // If key doesn't exist, then return.
    if (inputSetList == null) {
      return null;
    }
    Object finalValue = null;
    for (MergeVisitorInputSetElement inputSetElement : inputSetList) {
      if (inputSetElement != null) {
        Object inputSetFieldValue = ReflectionUtils.getFieldValue(inputSetElement.getInputSetElement(), field);
        if (inputSetFieldValue != null && !nullCheckPredicate.test(inputSetFieldValue)) {
          finalValue = inputSetFieldValue;
        }
      }
    }
    return finalValue;
  }

  private boolean isLeafFieldInputSetExpression(Object fieldValue, VisitorFieldRegistry visitorFieldRegistry) {
    String expressionFieldValue = "";
    if (fieldValue instanceof VisitorFieldWrapper) {
      VisitorFieldWrapper fieldWrapper = (VisitorFieldWrapper) fieldValue;
      VisitableFieldProcessor fieldProcessor = visitorFieldRegistry.obtain(fieldWrapper.getVisitorFieldType());
      // Null check
      if (fieldProcessor.isNull(fieldWrapper)) {
        return false;
      }
      expressionFieldValue = fieldProcessor.getExpressionFieldValue(fieldWrapper);
    } else if (fieldValue instanceof String) {
      expressionFieldValue = (String) fieldValue;
    }
    return NGExpressionUtils.matchesInputSetPattern(expressionFieldValue);
  }

  /**
   * This function adds corresponding inputSetElement fields to #elementToInputSetElementMap for accessing
   * when children are being visited.
   */
  public void addToWalkInputSetMap(Set<String> elementChildrenFieldNames, Field field, Object currentElement,
      Map<Object, List<MergeVisitorInputSetElement>> elementToInputSetElementListMap,
      Function<Object, Object> newDummyObjectFunction) throws IllegalAccessException {
    Object originalFieldValue = ReflectionUtils.getFieldValue(currentElement, field);

    // Check if the field is child or not from the set.
    if (originalFieldValue != null && elementChildrenFieldNames.contains(field.getName())) {
      List<MergeVisitorInputSetElement> inputSetElementsList = elementToInputSetElementListMap.get(currentElement);
      // If key doesn't exist, then return.
      if (inputSetElementsList == null) {
        return;
      }
      if (originalFieldValue instanceof List) {
        Map<Object, List<MergeVisitorInputSetElement>> objectListMap = findChildrenInsideInputSetElementsWithListType(
            inputSetElementsList, field, (List<Object>) originalFieldValue, newDummyObjectFunction);
        elementToInputSetElementListMap.putAll(objectListMap);
      } else {
        List<MergeVisitorInputSetElement> childrenInsideInputSetElementList =
            findChildrenInsideInputSetElements(inputSetElementsList, field, originalFieldValue, newDummyObjectFunction);
        elementToInputSetElementListMap.put(originalFieldValue, childrenInsideInputSetElementList);
      }
    }
  }

  /**
   * This function finds the corresponding children inside inputSetElementList which corresponds to
   * originalFieldValue, when originalFieldValue is a List.
   */
  private Map<Object, List<MergeVisitorInputSetElement>> findChildrenInsideInputSetElementsWithListType(
      List<MergeVisitorInputSetElement> inputSetElementList, Field field, List<Object> originalFieldValuesList,
      Function<Object, Object> newDummyObjectFunction) {
    // This map contains input set field elements which are corresponding to originalFieldValue.
    Map<Object, List<MergeVisitorInputSetElement>> originalFieldToInputSetFieldMap = new HashMap<>();

    Map<Object, Object> dummyFieldToOriginalFieldMap = new HashMap<>();
    for (Object originalFieldValue : originalFieldValuesList) {
      dummyFieldToOriginalFieldMap.put(newDummyObjectFunction.apply(originalFieldValue), originalFieldValue);
    }

    // This checks for those field values in each input set element whose dummy element is
    // equal to dummy element of originalFieldValue.
    for (MergeVisitorInputSetElement inputSetElement : inputSetElementList) {
      Object inputSetFieldList = ReflectionUtils.getFieldValue(inputSetElement.getInputSetElement(), field);
      if (inputSetFieldList != null) {
        if (inputSetFieldList instanceof List) {
          List<Object> fieldValuesList = (List<Object>) inputSetFieldList;

          for (Object inputSetField : fieldValuesList) {
            Object inputSetFieldDummy = newDummyObjectFunction.apply(inputSetField);
            if (dummyFieldToOriginalFieldMap.containsKey(inputSetFieldDummy)) {
              Object key = dummyFieldToOriginalFieldMap.get(inputSetFieldDummy);
              addToMapWithValuesList(originalFieldToInputSetFieldMap, key,
                  MergeVisitorInputSetElement.builder()
                      .inputSetIdentifier(inputSetElement.getInputSetIdentifier())
                      .inputSetElement(inputSetField)
                      .build());
            }
          }

        } else {
          String errorMessage = "Field type is not matching with original field type.";
          log.error(errorMessage);
          throw new InvalidArgumentsException(errorMessage);
        }
      }
    }
    return originalFieldToInputSetFieldMap;
  }

  /**
   * This function finds the corresponding children inside inputSetElements which corresponds to originalFieldValue,
   * when originalFieldValue is not a List
   * @return List of child fields of inputSetElements corresponding to originalFieldValue.
   */
  private List<MergeVisitorInputSetElement> findChildrenInsideInputSetElements(
      List<MergeVisitorInputSetElement> inputSetElementList, Field field, Object originalFieldValue,
      Function<Object, Object> newDummyObjectFunction) {
    // This list contains input set field elements which are corresponding to originalFieldValue.
    List<MergeVisitorInputSetElement> inputSetFieldElementsResult = new LinkedList<>();

    // This checks for those field values in each input set element whose dummy element is
    // equal to dummy element of originalFieldValue.
    for (MergeVisitorInputSetElement inputSetElement : inputSetElementList) {
      Object inputSetFieldValue = ReflectionUtils.getFieldValue(inputSetElement.getInputSetElement(), field);
      if (inputSetFieldValue != null) {
        if (checkIfInputSetFieldAtSameLevelOfOriginalField(
                originalFieldValue, inputSetFieldValue, newDummyObjectFunction)) {
          inputSetFieldElementsResult.add(MergeVisitorInputSetElement.builder()
                                              .inputSetIdentifier(inputSetElement.getInputSetIdentifier())
                                              .inputSetElement(inputSetFieldValue)
                                              .build());
        }
      }
    }
    return inputSetFieldElementsResult;
  }

  /**
   * This function checks whether inputSetField is corresponding to currentElementField in walking the tree.
   */
  public boolean checkIfInputSetFieldAtSameLevelOfOriginalField(
      Object currentElementField, Object inputSetElementField, Function<Object, Object> newDummyObjectFunction) {
    Object inputSetFieldDummy = newDummyObjectFunction.apply(inputSetElementField);
    Object originalFieldDummy = newDummyObjectFunction.apply(currentElementField);
    return inputSetFieldDummy.equals(originalFieldDummy);
  }

  /**
   * This function checks whether field is leaf property or not.
   */
  public boolean isElementFieldLeafProperty(Field elementField) {
    if (VisitorFieldWrapper.class.isAssignableFrom(elementField.getType())) {
      return true;
    } else if (elementField.getType() == String.class) {
      return true;
    }
    return elementField.getType().isPrimitive() || elementField.getType().isEnum();
  }

  /**
   * This function validates whether currentElementField is valid in all input set elements.
   */
  public VisitorErrorResponseWrapper isLeafInputSetFieldValid(Field elementField, String inputSetIdentifier,
      Object inputSetFieldValue, Object currentElementFieldValue, VisitorFieldRegistry visitorFieldRegistry)
      throws IllegalAccessException {
    VisitorErrorResponseWrapperBuilder responseWrapperBuilder = VisitorErrorResponseWrapper.builder();

    if (isFieldNull(inputSetFieldValue, visitorFieldRegistry)) {
      return responseWrapperBuilder.build();
    }

    // Field cannot be input set expression.
    String errorMessage = "Value inside input set cannot be another runtime expression.";
    Predicate<Object> isInputSetExpression = obj -> isLeafFieldInputSetExpression(obj, visitorFieldRegistry);
    MergeInputSetErrorResponse errorResponse = setErrorIfInputSetFieldNotValid(
        inputSetFieldValue, elementField.getName(), inputSetIdentifier, isInputSetExpression, errorMessage);

    if (errorResponse != null) {
      return responseWrapperBuilder.errors(Stream.of(errorResponse).collect(Collectors.toList())).build();
    }

    // If currentElementFieldValue is inputSet expression, then no error
    if (isLeafFieldInputSetExpression(currentElementFieldValue, visitorFieldRegistry)) {
      return responseWrapperBuilder.build();
    }

    // If not inputSet expression, then inputSet Element field should match the original fieldValue.
    errorMessage = "Input Set field cannot have value if not marked as runtime in original pipeline.";
    Predicate<Object> isInputSetValueValid = obj -> !obj.equals(currentElementFieldValue);
    errorResponse = setErrorIfInputSetFieldNotValid(
        inputSetFieldValue, elementField.getName(), inputSetIdentifier, isInputSetValueValid, errorMessage);

    if (errorResponse != null) {
      return responseWrapperBuilder.errors(Stream.of(errorResponse).collect(Collectors.toList())).build();
    }
    return responseWrapperBuilder.build();
  }

  /**
   * This function sets error if input set field is not valid.
   */
  private MergeInputSetErrorResponse setErrorIfInputSetFieldNotValid(Object inputSetFieldValue, String fieldName,
      String inputSetIdentifier, Predicate<Object> isInputSetValueValid, String errorMessage) {
    if (isInputSetValueValid.test(inputSetFieldValue)) {
      return MergeInputSetErrorResponse.mergeErrorBuilder()
          .fieldName(fieldName)
          .message(errorMessage)
          .identifierOfErrorSource(inputSetIdentifier)
          .build();
    }
    return null;
  }

  private void addToMapWithValuesList(
      Map<Object, List<MergeVisitorInputSetElement>> mapWithValuesList, Object key, MergeVisitorInputSetElement value) {
    if (mapWithValuesList.containsKey(key)) {
      mapWithValuesList.get(key).add(value);
    } else {
      mapWithValuesList.put(key, Stream.of(value).collect(Collectors.toList()));
    }
  }

  private boolean isFieldNull(Object fieldValue, VisitorFieldRegistry visitorFieldRegistry) {
    if (fieldValue == null) {
      return true;
    }

    if (fieldValue instanceof VisitorFieldWrapper) {
      VisitorFieldWrapper fieldWrapper = (VisitorFieldWrapper) fieldValue;
      VisitableFieldProcessor fieldProcessor = visitorFieldRegistry.obtain(fieldWrapper.getVisitorFieldType());
      return fieldProcessor.isNull(fieldWrapper);
    }
    return false;
  }

  /**
   * This function returns Map of field to fieldValue using reflection.
   */
  public Map<Field, Object> getFieldToFieldValueMap(Object element) {
    List<Field> elementFieldsList = ReflectionUtils.getAllDeclaredAndInheritedFields(element.getClass());
    Map<Field, Object> currentFieldToFieldValueMap = new HashMap<>();
    for (Field field : elementFieldsList) {
      Object fieldValue = ReflectionUtils.getFieldValue(element, field);
      currentFieldToFieldValueMap.put(field, fieldValue);
    }
    return currentFieldToFieldValueMap;
  }
}
