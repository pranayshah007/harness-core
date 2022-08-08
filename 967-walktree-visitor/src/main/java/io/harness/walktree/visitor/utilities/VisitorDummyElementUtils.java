/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.utilities;

import io.harness.exception.InvalidArgumentsException;
import io.harness.reflection.ReflectionUtils;
import io.harness.walktree.beans.VisitableChild;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.DummyVisitableElement;
import io.harness.walktree.visitor.SimpleVisitor;
import io.harness.walktree.visitor.Visitable;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VisitorDummyElementUtils {
  /**
   *
   * Adds the dummy child object to the dummy element of the parent.
   * NOTE: Only those child objects will be added that have an entry in the elementToDummyElementMap
   *
   * @param parentElement - the original element which contains visitable children
   * @param parentDummyElement - the object to which we want to add the dummy child objects
   * @param elementToDummyElementMap - the map consisting of original objects to dummy objects. Required for filtering
   *     getting the dummy element corresponding to the child.
   * @return
   */
  public VisitableChildren addDummyChildrenToGivenElement(
      Object parentElement, Object parentDummyElement, Map<Object, Object> elementToDummyElementMap) {
    Multimap<String, Object> fieldNameToDummyObjects =
        getFieldNamesToDummyObjects(parentElement, elementToDummyElementMap);
    if (fieldNameToDummyObjects.isEmpty()) {
      return VisitableChildren.builder().build();
    }
    VisitableChildren unhandledVisitableChildren = VisitableChildren.builder().build();
    for (String key : fieldNameToDummyObjects.keySet()) {
      Field field = ReflectionUtils.getFieldByName(parentDummyElement.getClass(), key);
      if (List.class.isAssignableFrom(field.getType())) {
        try {
          List<Object> childObjects = new ArrayList<>(fieldNameToDummyObjects.get(key));
          VisitorReflectionUtils.addValueToField(parentDummyElement, field, childObjects);
        } catch (Exception e) {
          // add to unhandled children so that we can give control to the VisitorHelper for the current object.
          fieldNameToDummyObjects.values().forEach(value -> unhandledVisitableChildren.add(key, value));
        }
      } else {
        for (Object value : fieldNameToDummyObjects.get(key)) {
          if (Map.class.isAssignableFrom(field.getType())) {
            unhandledVisitableChildren.add(key, value);
          } else {
            try {
              VisitorReflectionUtils.addValueToField(parentDummyElement, field, value);
            } catch (IllegalAccessException e) {
              unhandledVisitableChildren.add(key, value);
            }
          }
        }
      }
    }
    return unhandledVisitableChildren;
  }

  private Multimap<String, Object> getFieldNamesToDummyObjects(
      Object parentElement, Map<Object, Object> elementToDummyElementMap) {
    Multimap<String, Object> fieldNameToDummyObjects = LinkedHashMultimap.create();
    getDummyVisitableChildrenFromElementToDummyMap(parentElement, elementToDummyElementMap)
        .getVisitableChildList()
        .forEach(visitableChild
            -> fieldNameToDummyObjects.put(
                visitableChild.getFieldName(), elementToDummyElementMap.get(visitableChild.getValue())));
    return fieldNameToDummyObjects;
  }

  /**
   * Returns the dummy child of the given parentElement which exists in elementToDummyElementMap
   *
   * @param parentElement - the parent element from which we want to extract dummy children.
   * @param elementToDummyElementMap - A map consisting of element to dummy element.
   * @return
   */
  public VisitableChildren getDummyVisitableChildrenFromElementToDummyMap(
      Object parentElement, Map<Object, Object> elementToDummyElementMap) {
    if (parentElement instanceof Visitable) {
      VisitableChildren childrenToWalk = ((Visitable) parentElement).getChildrenToWalk();
      if (childrenToWalk == null) {
        return VisitableChildren.builder().build();
      }
      List<VisitableChild> dummyChildren =
          childrenToWalk.getVisitableChildList()
              .stream()
              .filter(visitableChild -> elementToDummyElementMap.containsKey(visitableChild.getValue()))
              .collect(Collectors.toList());
      return VisitableChildren.builder().visitableChildList(dummyChildren).build();
    }
    return VisitableChildren.builder().build();
  }

  public void addToDummyElementMapUsingPredicate(
      Map<Object, Object> elementToDummyElementMap, Object key, Object value, Predicate<Object> predicate) {
    if (predicate.test(key)) {
      addToDummyElementMap(elementToDummyElementMap, key, value);
    }
  }

  public void addToDummyElementMap(Map<Object, Object> elementToDummyElementMap, Object key, Object value) {
    if (elementToDummyElementMap.containsKey(key)) {
      return;
    }
    elementToDummyElementMap.put(key, value);
  }

  public Object getDummyElementFromMap(Map<Object, Object> elementToDummyElementMap, Object originalObject) {
    return elementToDummyElementMap.get(originalObject);
  }

  /**
   * Adds the child to the currentDummyElement.
   * Handles only user-defined objects and Lists. It does not handle collections or Maps type for now.
   * @return It returns those children which are not handled by it.
   */
  public void addChildrenToCurrentDummyElement(Object currentElement, Map<Object, Object> elementToDummyElementMap,
      SimpleVisitor<? extends DummyVisitableElement> visitor) {
    DummyVisitableElement helperClass = visitor.getHelperClass(currentElement);

    // If element has children and they are set in map then we want to merge them to dummy object for this element.
    VisitableChildren dummyVisitableChildrenFromElementToDummyMap =
        getDummyVisitableChildrenFromElementToDummyMap(currentElement, elementToDummyElementMap);

    if (dummyVisitableChildrenFromElementToDummyMap.isEmpty()) {
      return;
    }
    if (helperClass != null) {
      addToDummyElementMap(
          elementToDummyElementMap, currentElement, helperClass.createDummyVisitableElement(currentElement));
      VisitableChildren unhandledVisitableChildren = addDummyChildrenToGivenElement(
          currentElement, getDummyElementFromMap(elementToDummyElementMap, currentElement), elementToDummyElementMap);
      helperClass.handleComplexVisitableChildren(
          VisitorDummyElementUtils.getDummyElementFromMap(elementToDummyElementMap, currentElement), visitor,
          unhandledVisitableChildren);
    } else {
      throw new InvalidArgumentsException("DummyVisitableElement helper class cannot be null");
    }
  }

  /**
   * This function creates Set of children objects.
   */
  public Set<String> getChildrenFieldNames(Object currentElement) {
    Set<String> children = new HashSet<>();
    if (currentElement instanceof Visitable) {
      VisitableChildren childrenToWalk = ((Visitable) currentElement).getChildrenToWalk();
      if (childrenToWalk == null) {
        return children;
      }
      children =
          childrenToWalk.getVisitableChildList().stream().map(VisitableChild::getFieldName).collect(Collectors.toSet());
    }
    return children;
  }
}
