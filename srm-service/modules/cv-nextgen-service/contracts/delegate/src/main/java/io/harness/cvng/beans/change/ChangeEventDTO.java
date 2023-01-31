/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@SuperBuilder
@JsonDeserialize(using = ChangeEventDTODeserializer.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@FieldNameConstants
public class ChangeEventDTO {
  String id;
  @NotNull String accountId;
  @NotNull String orgIdentifier;
  @NotNull String projectIdentifier;

  String serviceIdentifier;
  String serviceName;
  // TODO: we need to add envIdentifiers
  String envIdentifier;
  String environmentName;

  String name;
  String monitoredServiceIdentifier;
  String changeSourceIdentifier;
  @JsonProperty("type") ChangeSourceType type;
  long eventTime;

  @Valid @NotNull ChangeEventMetadata metadata;

  public ChangeSourceType getType() {
    if (type == null && metadata != null) {
      type = metadata.getType();
    }
    return type;
  }

  public ChangeCategory getCategory() {
    return getType().getChangeCategory();
  }
}
