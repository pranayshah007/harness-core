/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModelProperty;
import java.util.Arrays;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "MonitoredServiceSpecKeys")
@JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
@OwnedBy(HarnessTeam.CV)
@SuperBuilder
@NoArgsConstructor
public abstract class MonitoredServiceSpec {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  public abstract String getType();

  public enum MonitoredServiceSpecType {
    DEFAULT("Default"),
    CONFIGURED("Configured"),
    TEMPLATE("Template");

    @Getter public final String name;
    MonitoredServiceSpecType(String name) {
      this.name = name;
    }

    public static MonitoredServiceSpecType getByName(String name) {
      return Arrays.stream(values())
          .filter(spec -> spec.name.equals(name))
          .findFirst()
          .orElseThrow(
              ()
                  -> new IllegalArgumentException(
                      "No enum constant of type io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType for name "
                      + name));
    }
  }
}
