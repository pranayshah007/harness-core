/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.mergeinputset;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.reflection.ReflectionUtils;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.registries.visitorfield.VisitorFieldRegistry;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;
import io.harness.walktree.visitor.DummyVisitableElement;
import io.harness.walktree.visitor.SimpleVisitor;
import io.harness.walktree.visitor.mergeinputset.beans.MergeVisitorInputSetElement;
import io.harness.walktree.visitor.response.VisitorErrorResponseWrapper;
import io.harness.walktree.visitor.response.VisitorResponse;
import io.harness.walktree.visitor.utilities.MergeInputSetHelperUtils;
import io.harness.walktree.visitor.utilities.VisitorDummyElementUtils;
import io.harness.walktree.visitor.utilities.VisitorResponseUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This visitor merges the given input set list with given object.
 * The #givenInputSetPipelineList should be passed same object class on which walking is to be done.
 * It also validates whether the input set is valid or not and stores the result in isResultValidInputSet.
 */
@Slf4j
public class MergeInputSetVisitor extends SimpleVisitor<DummyVisitableElement> {
  private final boolean useFQN;
  // This map is used to store merge response and its children.
  private final Map<Object, Object> elementToResponseDummyElementMap = new HashMap<>();

  // This map is used to traverse input sets parallel to original object.
  private final Map<Object, List<MergeVisitorInputSetElement>> elementToInputSetElementListMap = new HashMap<>();
  private final List<MergeVisitorInputSetElement> givenInputSetPipelineList = new LinkedList<>();
  @Inject private VisitorFieldRegistry visitorFieldRegistry;

  // This object stores the final result.
  @Getter private Object currentObjectResult;
  // This is identify if there are errors while merging.
  @Getter private boolean isResultValidInputSet;
  @Getter private Object currentObjectErrorResult;
  @Getter private final Map<String, VisitorResponse> uuidToErrorResponseMap = new HashMap<>();

  public MergeInputSetVisitor(
      Injector injector, boolean useFQN, List<MergeVisitorInputSetElement> givenInputSetPipelineList) {
    super(injector);
    this.useFQN = useFQN;
    this.givenInputSetPipelineList.addAll(givenInputSetPipelineList);
    this.isResultValidInputSet = true;
  }

  @Override
  public VisitElementResult preVisitElement(Object element) {
    // This is called to initialise elementToInputSetElementListMap.
    if (!elementToInputSetElementListMap.containsKey(element)) {
      if (!givenInputSetPipelineList.isEmpty()
          && MergeInputSetHelperUtils.checkIfInputSetFieldAtSameLevelOfOriginalField(
              element, givenInputSetPipelineList.get(0).getInputSetElement(), this::getNewDummyObject)) {
        elementToInputSetElementListMap.put(element, givenInputSetPipelineList);
      }
    }
    return super.preVisitElement(element);
  }

  @Override
  public VisitElementResult visitElement(Object currentElement) {
    Map<Field, Object> currentFieldToFieldValueMap = MergeInputSetHelperUtils.getFieldToFieldValueMap(currentElement);
    validateInputSetElements(currentElement, currentFieldToFieldValueMap);
    mergeInputSetsList(currentElement, currentFieldToFieldValueMap);
    return VisitElementResult.CONTINUE;
  }

  /**
   * This function checks whether all input sets elements are valid, if not create error object response.
   */
  private void validateInputSetElements(Object currentElement, Map<Field, Object> currentFieldToFieldValueMap) {
    Object dummyElement = getNewDummyObject(currentElement);

    boolean isValidInputSet = isValidInputSetElementsList(currentElement, dummyElement, currentFieldToFieldValueMap);
    VisitorDummyElementUtils.addToDummyElementMapUsingPredicate(
        getElementToDummyElementMap(), currentElement, dummyElement, element -> !isValidInputSet);
    this.isResultValidInputSet = this.isResultValidInputSet && isValidInputSet;
  }

  /**
   * This function is wrapper for merging input sets and adding dummy response element to map.
   */
  private void mergeInputSetsList(Object currentElement, Map<Field, Object> currentFieldToFieldValueMap) {
    Object dummyElement = getNewDummyObject(currentElement);

    mergeInputSetsInternal(currentElement, dummyElement, currentFieldToFieldValueMap);
    // Always true predicate
    VisitorDummyElementUtils.addToDummyElementMapUsingPredicate(
        elementToResponseDummyElementMap, currentElement, dummyElement, x -> true);
  }

  @Override
  public VisitElementResult postVisitElement(Object element) {
    addChildrenResponseToCurrentElement(element);
    addErrorChildrenResponseToCurrentElement(element);
    currentObjectResult = VisitorDummyElementUtils.getDummyElementFromMap(elementToResponseDummyElementMap, element);
    currentObjectErrorResult = VisitorDummyElementUtils.getDummyElementFromMap(getElementToDummyElementMap(), element);
    return super.postVisitElement(element);
  }

  /**
   * This function checks whether inputSet elements for given list matches the validation or not.
   * Validation -
   * 1. Input Set element field cannot be runtime expression like ${input}
   * 2. If not runtime expression, input Set element field cannot have a value which is not marked as runtime expression
   * in original pipeline, other than identifier, type which are required for list identification etc.
   */
  private boolean isValidInputSetElementsList(
      Object currentElement, Object dummyElement, Map<Field, Object> currentFieldToFieldValueMap) {
    List<MergeVisitorInputSetElement> inputSetElementsList = elementToInputSetElementListMap.get(currentElement);
    // If key doesn't exist, then return.
    if (inputSetElementsList == null) {
      return true;
    }

    boolean isValidInputSet = true;

    for (Map.Entry<Field, Object> fieldObjectEntry : currentFieldToFieldValueMap.entrySet()) {
      Field currentElementField = fieldObjectEntry.getKey();
      Object currentElementFieldValue = fieldObjectEntry.getValue();

      for (MergeVisitorInputSetElement inputSetElement : inputSetElementsList) {
        Object inputSetFieldValue =
            ReflectionUtils.getFieldValue(inputSetElement.getInputSetElement(), currentElementField);

        if (!MergeInputSetHelperUtils.isElementFieldLeafProperty(currentElementField)) {
          continue;
        }

        try {
          VisitorErrorResponseWrapper errorResponseWrapper = MergeInputSetHelperUtils.isLeafInputSetFieldValid(
              currentElementField, inputSetElement.getInputSetIdentifier(), inputSetFieldValue,
              currentElementFieldValue, visitorFieldRegistry);
          if (EmptyPredicate.isNotEmpty(errorResponseWrapper.getErrors())) {
            String uuid = VisitorResponseUtils.addUUIDValueToGivenField(
                this.getContextMap(), dummyElement, visitorFieldRegistry, currentElementField, useFQN);
            VisitorResponseUtils.addToVisitorResponse(
                uuid, errorResponseWrapper, uuidToErrorResponseMap, VisitorErrorResponseWrapper.builder().build());
            isValidInputSet = false;
          }
        } catch (IllegalAccessException e) {
          throw new InvalidArgumentsException(String.format("Error using reflection : %s", e.getMessage()));
        }
      }
    }

    return isValidInputSet;
  }

  /**
   * Used to validate the fields of the given object using
   * @param currentElement -  the original element of the original pipeline on which validation to work
   */
  @VisibleForTesting
  void mergeInputSetsInternal(
      Object currentElement, Object dummyElement, Map<Field, Object> currentFieldToFieldValueMap) {
    Set<String> currentElementChildrenFieldNames = VisitorDummyElementUtils.getChildrenFieldNames(currentElement);

    // This loop checks only leaf properties which are either String or VisitorFieldWrapper, as they can be runtime
    // expressions i.e ${input}
    try {
      for (Map.Entry<Field, Object> fieldObjectEntry : currentFieldToFieldValueMap.entrySet()) {
        Field field = fieldObjectEntry.getKey();
        Object fieldValue = fieldObjectEntry.getValue();

        // Only add values to response if inputSets elements are valid.
        if (this.isResultValidInputSet && fieldValue != null) {
          if (VisitorFieldWrapper.class.isAssignableFrom(field.getType())) {
            MergeInputSetHelperUtils.addFieldValueForVisitableFieldWrapper(
                currentElement, dummyElement, field, fieldValue, visitorFieldRegistry, elementToInputSetElementListMap);
          } else if (field.getType() == String.class) {
            MergeInputSetHelperUtils.addFieldValueForStringField(
                currentElement, dummyElement, field, fieldValue, elementToInputSetElementListMap);
          } else if (field.getType().isPrimitive() || field.getType().isEnum()) {
            MergeInputSetHelperUtils.addFieldValueForEnumOrPrimitiveType(dummyElement, field, fieldValue);
          }
        }

        // Add inputSet currentElementChildren fields to map.
        MergeInputSetHelperUtils.addToWalkInputSetMap(currentElementChildrenFieldNames, field, currentElement,
            elementToInputSetElementListMap, this::getNewDummyObject);
      }
    } catch (IllegalAccessException e) {
      throw new InvalidArgumentsException(String.format("Error using reflection : %s", e.getMessage()));
    }
  }

  /**
   * This functions adds children of the current element to the currentDummyElement from the given map.
   */
  void addChildrenResponseToCurrentElement(Object currentElement) {
    VisitorDummyElementUtils.addChildrenToCurrentDummyElement(currentElement, elementToResponseDummyElementMap, this);
  }

  /**
   * This functions adds error children of the current element to the currentDummyElement from the given map.
   */
  void addErrorChildrenResponseToCurrentElement(Object currentElement) {
    VisitorDummyElementUtils.addChildrenToCurrentDummyElement(currentElement, getElementToDummyElementMap(), this);
  }
}
