package io.harness.plancreator.strategy;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.plancreator.strategy.ExpressionAxisConfig")
@JsonRootName("")
public class ExpressionAxisConfig {
  ParameterField<Object> expression;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public ExpressionAxisConfig(ParameterField<Object> expression) {
    this.expression = expression;
  }

  @JsonValue
  public ParameterField<Object> toJson() {
    return expression;
  }
}
