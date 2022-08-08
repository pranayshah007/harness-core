/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.CastedField;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterDocumentField.ParameterDocumentFieldKeys;
import io.harness.utils.RecastReflectionUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class ParameterDocumentFieldMapper {
  public ParameterDocumentField fromParameterField(ParameterField<?> parameterField, CastedField castedField) {
    boolean skipAutoEvaluation = castedField != null && castedField.getField() != null
        && castedField.getField().isAnnotationPresent(SkipAutoEvaluation.class);
    Class<?> cls = findValueClass(null, castedField);
    if (parameterField == null) {
      if (cls == null) {
        throw new InvalidRequestException("Parameter field is null");
      }
      return ParameterDocumentField.builder()
          .valueDoc(RecastOrchestrationUtils.toMap(new ParameterFieldValueWrapper<>(null)))
          .valueClass(cls.getName())
          .typeString(cls.isAssignableFrom(String.class))
          .skipAutoEvaluation(skipAutoEvaluation)
          .build();
    }
    return ParameterDocumentField.builder()
        .expression(parameterField.isExpression())
        .expressionValue(parameterField.getExpressionValue())
        .valueDoc(RecastOrchestrationUtils.toMap(new ParameterFieldValueWrapper<>(parameterField.getValue())))
        .valueClass(cls == null ? null : cls.getName())
        .inputSetValidator(parameterField.getInputSetValidator())
        .typeString(parameterField.isTypeString())
        .skipAutoEvaluation(skipAutoEvaluation)
        .jsonResponseField(parameterField.isJsonResponseField())
        .responseField(parameterField.getResponseField())
        .build();
  }

  public ParameterField<?> toParameterField(ParameterDocumentField documentField) {
    if (documentField == null) {
      return null;
    }

    ParameterFieldValueWrapper<?> parameterFieldValueWrapper =
        RecastOrchestrationUtils.fromMap(documentField.getValueDoc(), ParameterFieldValueWrapper.class);
    checkValueClass(documentField, parameterFieldValueWrapper);
    return ParameterField.builder()
        .expression(documentField.isExpression())
        .expressionValue(documentField.getExpressionValue())
        .value(parameterFieldValueWrapper == null ? null : parameterFieldValueWrapper.getValue())
        .inputSetValidator(documentField.getInputSetValidator())
        .typeString(documentField.isTypeString())
        .jsonResponseField(documentField.isJsonResponseField())
        .responseField(documentField.getResponseField())
        .build();
  }

  private void checkValueClass(
      ParameterDocumentField documentField, ParameterFieldValueWrapper<?> parameterFieldValueWrapper) {
    if (documentField.getValueClass() == null || parameterFieldValueWrapper == null
        || parameterFieldValueWrapper.getValue() == null) {
      return;
    }

    Class<?> cls;
    try {
      cls = Class.forName(documentField.getValueClass());
    } catch (Exception ex) {
      // For backwards compatibility
      log.warn("Unknown class: {}", documentField.getValueClass());
      return;
    }

    if (!cls.isAssignableFrom(parameterFieldValueWrapper.getValue().getClass())) {
      log.error(String.format("Expected value of type: %s, got: %s", cls.getSimpleName(),
          parameterFieldValueWrapper.getValue().getClass().getSimpleName()));
    }
  }

  public Optional<ParameterDocumentField> fromParameterFieldMap(Object o) {
    if (!(o instanceof Map)) {
      return Optional.empty();
    }

    Map<String, Object> map = (Map<String, Object>) o;
    Object recastClass = Optional.ofNullable(RecastReflectionUtils.getIdentifier(map)).orElse("");
    String recasterAliasValue = RecastReflectionUtils.obtainRecasterAliasValueOrNull(ParameterField.class);
    if (!recastClass.equals(ParameterField.class.getName()) && !recastClass.equals(recasterAliasValue)) {
      return Optional.empty();
    }

    Map<String, Object> encodedValue = (Map<String, Object>) RecastOrchestrationUtils.getEncodedValue(map);
    return Optional.ofNullable(fromMap(encodedValue));
  }

  public ParameterDocumentField fromMap(Map<String, Object> map) {
    if (map == null) {
      return null;
    }

    // Temporarily store valueDoc so that recaster doesn't deserialize it
    Map<String, Object> valueDoc = (Map<String, Object>) map.get(ParameterDocumentFieldKeys.valueDoc);
    map.put(ParameterDocumentFieldKeys.valueDoc, null);

    ParameterDocumentField docField = RecastOrchestrationUtils.fromMap(map, ParameterDocumentField.class);
    if (docField == null) {
      return null;
    }

    // Reset valueDoc
    map.put(ParameterDocumentFieldKeys.valueDoc, valueDoc);
    docField.setValueDoc(valueDoc);
    return docField;
  }

  private Class<?> findValueClass(ParameterField<?> parameterField, CastedField castedField) {
    if (parameterField != null && parameterField.getValue() != null) {
      return parameterField.getValue().getClass();
    }
    if (castedField == null || !(castedField.getGenericType() instanceof ParameterizedType)) {
      return null;
    }
    return getFinalClass(((ParameterizedType) castedField.getGenericType()).getActualTypeArguments()[0]);
  }

  private Class<?> getFinalClass(Type type) {
    if (type instanceof Class<?>) {
      return (Class<?>) type;
    } else if (type instanceof ParameterizedType) {
      return ((ParameterizedType) type).getRawType().getClass();
    }
    return null;
  }
}
