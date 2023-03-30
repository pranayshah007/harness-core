/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.serializer.jackson;

import io.harness.beans.InputSetValidatorType;
import io.harness.common.NGExpressionUtils;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import io.serializer.utils.NGRuntimeInputUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class ParameterFieldDeserializer extends StdDeserializer<ParameterField<?>> implements ContextualDeserializer {
  private static final long serialVersionUID = 1L;
  protected final JavaType fullType;
  protected final JavaType referenceType;
  protected transient JsonDeserializer<?> valueDeserializer;
  protected transient TypeDeserializer valueTypeDeserializer;

  private final List<InputSetValidatorTypeWithPattern> inputSetValidationPatternList;

  public ParameterFieldDeserializer(
      JavaType fullType, JavaType refType, TypeDeserializer typeDeser, JsonDeserializer<?> valueDeser) {
    super(fullType);
    this.fullType = fullType;
    referenceType = refType;
    valueTypeDeserializer = typeDeser;
    valueDeserializer = valueDeser;
    inputSetValidationPatternList = initialiseInputSetValidationPatternList();
  }

  protected ParameterFieldDeserializer withResolved(
      JavaType refType, TypeDeserializer typeDeser, JsonDeserializer<?> valueDeser) {
    if ((refType == referenceType) && (valueDeser == valueDeserializer) && (typeDeser == valueTypeDeserializer)) {
      return this;
    }
    return new ParameterFieldDeserializer(fullType, refType, typeDeser, valueDeser);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
      throws JsonMappingException {
    JsonDeserializer<?> deser = valueDeserializer;
    TypeDeserializer typeDeser = valueTypeDeserializer;
    JavaType refType = referenceType;

    if (deser == null) {
      deser = ctxt.findContextualValueDeserializer(refType, property);
    } else { // otherwise directly assigned, probably not contextual yet:
      deser = ctxt.handleSecondaryContextualization(deser, property, refType);
    }
    if (typeDeser != null) {
      typeDeser = typeDeser.forProperty(property);
    }
    return withResolved(refType, typeDeser, deser);
  }

  @Override
  public ParameterField<?> getNullValue(DeserializationContext ctxt) {
    return ParameterField.ofNull();
  }

  @Override
  public ParameterField<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String text = p.getText().trim();
    boolean isTypeString = this.referenceType.getRawClass().equals(String.class);

    InputSetValidator inputSetValidator = getInputSetValidator(text);

    if (NGExpressionUtils.matchesInputSetPattern(text)) {
      String defaultValue = NGRuntimeInputUtils.extractParameters(text, "default");
      boolean isExecutionInput = NGExpressionUtils.matchesExecutionInputPattern(text);
      // if default is not null then keep the isExpression field false. As the default value should be treated as final
      // value. This will be useful in current Runtime inputs. In Execution inputs, we give the input template with
      // pre-filled default values. So user will always provide value.
      return ParameterField.createFieldWithDefaultValue(defaultValue == null, isExecutionInput,
          NGExpressionUtils.DEFAULT_INPUT_SET_EXPRESSION, extractDefaultValue(defaultValue), inputSetValidator,
          isTypeString);
    }
    if (inputSetValidator != null && isTypeString) {
      String value = getLeftSideOfExpression(text);
      if (EngineExpressionEvaluator.hasExpressions(value)) {
        return ParameterField.createExpressionField(true, value, inputSetValidator, true);
      }
      return ParameterField.createValueFieldWithInputSetValidator(value, inputSetValidator, true);
    } else if (inputSetValidator != null) {
      String value = getLeftSideOfExpression(text);
      if (EngineExpressionEvaluator.hasExpressions(value)) {
        return ParameterField.createExpressionField(true, value, inputSetValidator, false);
      }
      ObjectMapper mapper = (ObjectMapper) p.getCodec();
      Object trueValue = mapper.readValue(value, referenceType.getRawClass());
      return ParameterField.createValueFieldWithInputSetValidator(trueValue, inputSetValidator, false);
    }
    if (EngineExpressionEvaluator.hasExpressions(text)) {
      return ParameterField.createExpressionField(true, text, null, isTypeString);
    }

    Object refd = (valueTypeDeserializer == null)
        ? valueDeserializer.deserialize(p, ctxt)
        : valueDeserializer.deserializeWithType(p, ctxt, valueTypeDeserializer);

    return ParameterField.createValueField(refd);
  }

  @Override
  public ParameterField<?> deserializeWithType(
      JsonParser p, DeserializationContext ctxt, TypeDeserializer typeDeserializer) throws IOException {
    final JsonToken t = p.getCurrentToken();
    if (t == JsonToken.VALUE_NULL) {
      return getNullValue(ctxt);
    }

    if (t != null && t.isScalarValue()) {
      return deserialize(p, ctxt);
    }
    return (ParameterField<?>) typeDeserializer.deserializeTypedFromAny(p, ctxt);
  }

  private InputSetValidator getInputSetValidator(String field) {
    for (InputSetValidatorTypeWithPattern validatorTypeWithPattern : inputSetValidationPatternList) {
      if (NGExpressionUtils.containsPattern(validatorTypeWithPattern.validatorPattern, field)) {
        // This will get the content inside the validation pattern.
        //        String validationParameters = validatorTypeWithPattern.validatorPattern.split(field)[1];
        String validationParameters =
            NGRuntimeInputUtils.extractParameters(field, validatorTypeWithPattern.validatorType.getYamlName());
        return new InputSetValidator(validatorTypeWithPattern.validatorType, validationParameters);
      }
    }
    return null;
  }

  private String getLeftSideOfExpression(String field) {
    for (InputSetValidatorTypeWithPattern validatorTypeWithPattern : inputSetValidationPatternList) {
      if (NGExpressionUtils.containsPattern(validatorTypeWithPattern.validatorPattern, field)) {
        // This will get the content before the validation pattern
        return validatorTypeWithPattern.validatorPattern.split(field)[0];
      }
    }
    return null;
  }

  private List<InputSetValidatorTypeWithPattern> initialiseInputSetValidationPatternList() {
    List<InputSetValidatorTypeWithPattern> validatorTypeWithPatterns = new LinkedList<>();
    for (InputSetValidatorType value : InputSetValidatorType.values()) {
      String pattern = NGExpressionUtils.getInputSetValidatorPattern(value.getYamlName());
      validatorTypeWithPatterns.add(new InputSetValidatorTypeWithPattern(value, Pattern.compile(pattern)));
    }
    return validatorTypeWithPatterns;
  }

  public static class InputSetValidatorTypeWithPattern {
    InputSetValidatorType validatorType;
    Pattern validatorPattern;

    public InputSetValidatorTypeWithPattern(InputSetValidatorType validatorType, Pattern validatorPattern) {
      this.validatorType = validatorType;
      this.validatorPattern = validatorPattern;
    }
  }
  private Object extractDefaultValue(String defaultValuesString) {
    if (defaultValuesString == null) {
      return null;
    }
    defaultValuesString = processDefaultValueString(defaultValuesString);
    JsonNode jsonNode = JsonUtils.readTree("{\"default\":" + defaultValuesString + "}").get("default");
    if (jsonNode.isArray()) {
      List<Object> array = new ArrayList<>();
      for (JsonNode arrayElement : jsonNode) {
        array.add(JsonNodeUtils.getValueFromJsonNode(arrayElement));
      }
      return array;
    } else if (jsonNode.isTextual() && this.referenceType.getRawClass() == String.class) {
      return jsonNode.asText();
    }
    return JsonUtils.asObject(defaultValuesString, this.referenceType.getRawClass());
  }

  private String processDefaultValueString(String defaultValuesString) {
    if (!(defaultValuesString.charAt(0) == '[' && defaultValuesString.endsWith("]")
            || defaultValuesString.charAt(0) == '"' && defaultValuesString.endsWith("\""))) {
      return "\"" + defaultValuesString + "\"";
    }
    return defaultValuesString;
  }
}
