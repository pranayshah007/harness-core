/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.utilities;

import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.reflection.ReflectionUtils;
import io.harness.walktree.registries.visitorfield.VisitableFieldProcessor;
import io.harness.walktree.registries.visitorfield.VisitorFieldRegistry;
import io.harness.walktree.registries.visitorfield.VisitorFieldType;
import io.harness.walktree.registries.visitorfield.VisitorFieldWrapper;
import io.harness.walktree.visitor.WithMetadata;
import io.harness.walktree.visitor.response.VisitorResponse;

import java.lang.reflect.Field;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VisitorResponseUtils {
  /**
   * Adds the given uuid -> validationErrors to the uuidToErrorMap
   * Appends to the given uuid if the uuid already exists.
   * @param uuid
   * @param visitorResponse
   */
  public void addToVisitorResponse(String uuid, VisitorResponse visitorResponse,
      Map<String, VisitorResponse> uuidToErrorResponseMap, VisitorResponse newObject) {
    VisitorResponse existingValidationResponseList = uuidToErrorResponseMap.getOrDefault(uuid, newObject);
    existingValidationResponseList.add(visitorResponse);
    uuidToErrorResponseMap.put(uuid, visitorResponse);
  }

  public String addUUIDValueToGivenField(Map<String, Object> contextMap, Object element,
      VisitorFieldRegistry visitorFieldRegistry, Field field, boolean useFQN) throws IllegalAccessException {
    field.setAccessible(true);
    if (VisitorFieldWrapper.class.isAssignableFrom(field.getType())) {
      return addWrappedUUID(contextMap, visitorFieldRegistry, element, field, useFQN);
    } else if (field.getType() == String.class) {
      return addUUID(contextMap, element, field, useFQN);
    }
    return addUUIDToMetaDataField(contextMap, element, useFQN);
  }

  public String getUUid(Map<String, Object> contextMap, String fieldName, boolean useFQN) {
    return useFQN ? VisitorParentPathUtils.getFullQualifiedDomainName(contextMap)
            + VisitorParentPathUtils.PATH_CONNECTOR + fieldName
                  : UUIDGenerator.generateUuid();
  }

  private String addWrappedUUID(Map<String, Object> contextMap, VisitorFieldRegistry visitorFieldRegistry,
      Object element, Field field, boolean useFQN) throws IllegalAccessException {
    if (field.get(element) == null) {
      String uuid = getUUid(contextMap, field.getName(), useFQN);
      Class<? extends VisitorFieldWrapper> fieldType = (Class<? extends VisitorFieldWrapper>) field.getType();
      VisitableFieldProcessor<?> visitableFieldProcessor =
          visitorFieldRegistry.obtain(visitorFieldRegistry.obtainFieldType(fieldType));

      field.set(element, visitableFieldProcessor.createNewFieldWithStringValue(uuid));
      return uuid;
    }
    VisitorFieldWrapper parameterField = (VisitorFieldWrapper) field.get(element);
    VisitorFieldType visitorFieldType = visitorFieldRegistry.obtainFieldType(parameterField.getClass());
    VisitableFieldProcessor visitorFieldWrapper = visitorFieldRegistry.obtain(visitorFieldType);
    return visitorFieldWrapper.getFieldWithStringValue(parameterField);
  }

  private String addUUID(Map<String, Object> contextMap, Object element, Field field, boolean useFQN)
      throws IllegalAccessException {
    if (field.get(element) == null) {
      String uuid = getUUid(contextMap, field.getName(), useFQN);
      field.set(element, uuid);
      return uuid;
    }
    return (String) field.get(element);
  }

  private String addUUIDToMetaDataField(Map<String, Object> contextMap, Object element, boolean useFQN)
      throws IllegalAccessException {
    if (element instanceof WithMetadata) {
      Field field = ReflectionUtils.getFieldByName(element.getClass(), "metadata");
      if (field == null) {
        throw new IllegalAccessException();
      }
      field.setAccessible(true);
      if (field.get(element) == null) {
        String uuid = getUUid(contextMap, field.getName(), useFQN);
        field.set(element, uuid);
        return uuid;
      }
      return (String) field.get(element);
    }
    throw new InvalidRequestException("There is no field with Metadata.");
  }
}
