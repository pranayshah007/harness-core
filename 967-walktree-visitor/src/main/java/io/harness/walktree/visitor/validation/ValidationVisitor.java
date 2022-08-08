/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.validation;

import io.harness.reflection.ReflectionUtils;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.registries.visitorfield.VisitorFieldRegistry;
import io.harness.walktree.visitor.SimpleVisitor;
import io.harness.walktree.visitor.response.VisitorErrorResponse;
import io.harness.walktree.visitor.response.VisitorErrorResponseWrapper;
import io.harness.walktree.visitor.response.VisitorResponse;
import io.harness.walktree.visitor.utilities.VisitorDummyElementUtils;
import io.harness.walktree.visitor.utilities.VisitorResponseUtils;
import io.harness.walktree.visitor.validation.modes.ModeType;
import io.harness.walktree.visitor.validation.modes.PostInputSet;
import io.harness.walktree.visitor.validation.modes.PreInputSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class ValidationVisitor extends SimpleVisitor<ConfigValidator> {
  @Inject private VisitorFieldRegistry visitorFieldRegistry;

  private final boolean useFQN;
  private final Class<?> modeType;
  @Getter private Object currentObject;

  @Getter Map<String, VisitorResponse> uuidToVisitorResponse = new HashMap<>();

  public ValidationVisitor(Injector injector, Class<?> modeType, boolean useFQN) {
    super(injector);
    this.modeType = modeType;
    this.useFQN = useFQN;
  }

  @Override
  public VisitElementResult postVisitElement(Object element) {
    addErrorChildrenToCurrentElement(element);
    currentObject = VisitorDummyElementUtils.getDummyElementFromMap(getElementToDummyElementMap(), element);
    return super.postVisitElement(element);
  }

  @Override
  public VisitElementResult visitElement(Object currentElement) {
    ConfigValidator helperClass = getHelperClass(currentElement);
    Object dummyElement;
    if (helperClass != null) {
      dummyElement = helperClass.createDummyVisitableElement(currentElement);
    } else {
      log.error("Helper Class not implemented for object of type" + currentElement.getClass());
      throw new NotImplementedException("Helper Class not implemented for object of type" + currentElement.getClass());
    }
    boolean hasError = validateAnnotations(currentElement, dummyElement);
    VisitorDummyElementUtils.addToDummyElementMapUsingPredicate(
        getElementToDummyElementMap(), currentElement, dummyElement, object -> hasError);

    helperClass.validate(currentElement, this);
    return VisitElementResult.CONTINUE;
  }

  /**
   * Used to validate the fields of the given object using
   * @param currentElement -  the original element on which validation to work
   * @param dummyElement - the dummy element returned by HelperClass#getDummyObject.
   * @return
   */
  @VisibleForTesting
  boolean validateAnnotations(Object currentElement, Object dummyElement) {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    if (modeType == ModeType.POST_INPUT_SET) {
      violations.addAll(validator.validate(currentElement, PostInputSet.class));
    } else {
      violations.addAll(validator.validate(currentElement, PreInputSet.class));
    }
    if (!violations.isEmpty()) {
      generateUUIDAndAddToVisitorResponse(violations, dummyElement);
      return true;
    }
    return false;
  }

  private void generateUUIDAndAddToVisitorResponse(Set<ConstraintViolation<Object>> violations, Object dummyElement) {
    Multimap<String, String> errorsMap = ArrayListMultimap.create();
    violations.forEach(objectConstraintViolation
        -> errorsMap.put(
            objectConstraintViolation.getPropertyPath().toString(), objectConstraintViolation.getMessage()));
    for (String fieldName : errorsMap.keys()) {
      try {
        List<String> errorMessages = new ArrayList<>(errorsMap.get(fieldName));
        VisitorErrorResponseWrapper visitorErrorResponseWrapper =
            VisitorErrorResponseWrapper.builder()
                .errors(errorMessages.stream()
                            .map(message
                                -> VisitorErrorResponse.errorBuilder().fieldName(fieldName).message(message).build())
                            .collect(Collectors.toList()))
                .build();
        Field field = ReflectionUtils.getFieldByName(dummyElement.getClass(), fieldName);
        String uuid = VisitorResponseUtils.addUUIDValueToGivenField(
            this.getContextMap(), dummyElement, visitorFieldRegistry, field, useFQN);
        VisitorResponseUtils.addToVisitorResponse(
            uuid, visitorErrorResponseWrapper, uuidToVisitorResponse, VisitorErrorResponseWrapper.builder().build());

      } catch (IllegalAccessException e) {
        log.error(String.format("Error using reflection : %s", e.getMessage()));
      }
    }
  }

  /**
   * Adds the child to the currentDummyElement.
   * Handles only user-defined objects. It does not handle collections or Maps type for now.
   */
  @VisibleForTesting
  void addErrorChildrenToCurrentElement(Object element) {
    VisitorDummyElementUtils.addChildrenToCurrentDummyElement(element, getElementToDummyElementMap(), this);
  }
}
