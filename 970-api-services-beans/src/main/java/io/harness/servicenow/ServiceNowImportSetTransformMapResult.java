/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;
import io.harness.servicenow.deserializer.ServiceNowImportSetTransformMapResultDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ServiceNowImportSetTransformMapResultDeserializer.class)
public class ServiceNowImportSetTransformMapResult {
  @JsonProperty("target_record_url") String targetRecordURL;
  @JsonProperty("target_table") String targetTable;
  @JsonProperty("transform_map") @NotNull String transformMap;
  @NotNull String status;
  @JsonProperty("display_name") String displayName;
  @JsonProperty("display_value") String displayValue;
  @JsonProperty("error_message") String errorMessage;
  @JsonProperty("status_message") String statusMessage;

  public ServiceNowImportSetTransformMapResult(JsonNode node) {
    // these two fields are always present even in error case
    this.transformMap = JsonNodeUtils.mustGetString(node, "transform_map");
    this.status = JsonNodeUtils.mustGetString(node, "status");

    this.targetTable = JsonNodeUtils.getString(node, "target_table");
    this.targetRecordURL = JsonNodeUtils.getString(node, "target_record_url");
    this.displayName = JsonNodeUtils.getString(node, "display_name");
    this.displayValue = JsonNodeUtils.getString(node, "display_value");
    this.errorMessage = JsonNodeUtils.getString(node, "error_message");
    this.statusMessage = JsonNodeUtils.getString(node, "status_message");
  }
}