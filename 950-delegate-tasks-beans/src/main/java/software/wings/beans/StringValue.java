/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static java.util.Collections.emptyList;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;

import software.wings.audit.ResourceType;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Objects;

/**
 * Created by peeyushaggarwal on 6/9/16.
 */
@JsonTypeName("STRING")
public class StringValue extends SettingValue {
  /**
   * The String value.
   */
  private String value;

  /**
   * Instantiates a new String setting value.
   */
  public StringValue() {
    super(SettingVariableTypes.STRING.name());
  }

  /**
   * Getter for property 'value'.
   *
   * @return Value for property 'value'.
   */
  public String getValue() {
    return value;
  }

  /**
   * Setter for property 'value'.
   *
   * @param value Value to set for property 'value'.
   */
  public void setValue(String value) {
    this.value = value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    StringValue other = (StringValue) obj;
    return Objects.equals(value, other.value);
  }

  @Override
  public String fetchResourceCategory() {
    return ResourceType.SETTING.name();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return emptyList();
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    /**
     * The Value.
     */
    private String value;

    private Builder() {}

    /**
     * A string setting value builder.
     *
     * @return the builder
     */
    public static Builder aStringValue() {
      return new Builder();
    }

    /**
     * With value builder.
     *
     * @param value the value
     * @return the builder
     */
    public Builder withValue(String value) {
      this.value = value;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aStringValue().withValue(value);
    }

    /**
     * Build string setting value.
     *
     * @return the string setting value
     */
    public StringValue build() {
      StringValue stringValue = new StringValue();
      stringValue.setValue(value);
      return stringValue;
    }
  }
}
