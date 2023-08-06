/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.fieldrecaster;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.CastedField;
import io.harness.beans.RecasterMap;
import io.harness.core.Recaster;
import io.harness.exception.CastedFieldException;
import io.harness.exception.ParameterFieldCastException;
import io.harness.exceptions.RecasterException;
import io.harness.utils.RecastReflectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ComplexFieldRecaster implements FieldRecaster {
  @SuppressWarnings("unchecked")
  @Override
  public void fromMap(Recaster recaster, RecasterMap recasterMap, CastedField cf, Object entity) {
    try {
      final Object docVal = cf.getRecastedMapValue(recasterMap);
      if (docVal != null) {
        Object refObj;
        if (recaster.getTransformer().hasSimpleValueTransformer(cf.getType())) {
          refObj = recaster.getTransformer().decode(cf.getType(), docVal, cf);
        } else if (!(docVal instanceof Map) && recaster.getTransformer().hasSimpleValueTransformer(docVal)) {
          // special case for parameterized classes. E.x: Dummy<T>
          refObj = recaster.getTransformer().decode(docVal.getClass(), docVal, cf);
        } else {
          RecasterMap value = RecasterMap.cast((Map<String, Object>) docVal);
          if (!value.containsIdentifier()) {
            refObj = recaster.getTransformer().decode(LinkedHashMap.class, value, null);
          } else if (recaster.getTransformer().hasCustomTransformer(RecastReflectionUtils.getClass(value))) {
            refObj = recaster.getTransformer().decode(RecastReflectionUtils.getClass(value), value, cf);
          } else {
            refObj = recaster.getObjectFactory().createInstance(recaster, cf, value);
            refObj = recaster.fromMap(value, refObj);
          }
        }
        if (refObj != null) {
          cf.setFieldValue(entity, refObj);
        }
      }
    } catch (Exception e) {
      throwErrorBasedOnException(e, cf);
    }
  }

  private void throwErrorBasedOnException(Exception e, CastedField cf) {
    if (e instanceof ParameterFieldCastException) {
      String fieldName = cf.getField().getName();
      String message = "Exception while resolving the field [%s]; Cause: " + e.getMessage();
      // This exception is thrown when there's a class cast error in field of type parameterField
      throw new RecasterException(format(message, fieldName), e, fieldName, message);
    } else if (e instanceof CastedFieldException) {
      // We are building path of field by appending the field name from the caught exception
      String newFieldPath = cf.getField().getName() + "." + ((CastedFieldException) e).getFieldPath();
      String messageWithoutFieldPath = ((CastedFieldException) e).getMessageWithoutFieldPath();
      throw new RecasterException(
          format(messageWithoutFieldPath, newFieldPath), e, newFieldPath, messageWithoutFieldPath);
    } else {
      throw new RecasterException(
          String.format("Exception while processing complex field [%s]", cf.getField().getName()), e);
    }
  }

  @Override
  public void toMap(Recaster recaster, Object entity, CastedField cf, RecasterMap recasterMap) {
    final String name = cf.getNameToStore();

    final Object fieldValue = cf.getFieldValue(entity);

    if (recaster.getTransformer().hasSimpleValueTransformer(fieldValue)) {
      recaster.getTransformer().putToMap(entity, cf, recasterMap);
      return;
    }

    if (recaster.getTransformer().hasCustomTransformer(cf.getType())) {
      recasterMap.append(cf.getNameToStore(), obtainEncodedValueInternal(recaster, cf, fieldValue));
      return;
    }

    final Map<String, Object> map = fieldValue == null ? null : recaster.toMap(fieldValue);
    if (map != null && !map.keySet().isEmpty()) {
      recasterMap.append(name, map);
    }
  }

  private Map<String, Object> obtainEncodedValueInternal(Recaster recaster, CastedField cf, Object fieldValue) {
    RecasterMap recastedMap = new RecasterMap();
    recastedMap.setIdentifier(cf.getType());
    recastedMap.put(Recaster.ENCODED_VALUE, recaster.getTransformer().encode(cf.getType(), fieldValue, cf));
    return recastedMap;
  }
}
