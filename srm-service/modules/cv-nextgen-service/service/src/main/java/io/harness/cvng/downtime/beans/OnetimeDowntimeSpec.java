/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.downtime.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
public class OnetimeDowntimeSpec extends DowntimeSpec {
  @JsonProperty("type") OnetimeDowntimeType type;
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  @NotNull
  OnetimeSpec spec;

  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OnetimeDurationBasedSpec.class, name = "Duration")
    , @JsonSubTypes.Type(value = OnetimeEndTimeBasedSpec.class, name = "EndTime"),
  })
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
  public abstract static class OnetimeSpec {
    @JsonIgnore public abstract OnetimeDowntimeType getType();
  }
  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OnetimeDurationBasedSpec extends OnetimeSpec {
    @NotNull private DowntimeDuration downtimeDuration;
    @Override
    public OnetimeDowntimeType getType() {
      return OnetimeDowntimeType.DURATION;
    }
  }
  @SuperBuilder
  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OnetimeEndTimeBasedSpec extends OnetimeSpec {
    @Deprecated private long endTime;
    private String endDateTime;
    @Override
    public OnetimeDowntimeType getType() {
      return OnetimeDowntimeType.END_TIME;
    }
  }

  @Override
  public DowntimeType getType() {
    return DowntimeType.ONE_TIME;
  }
}
