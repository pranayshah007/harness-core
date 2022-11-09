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
import io.harness.servicenow.deserializer.ServiceNowImportSetResponseNGDeserializer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;
import java.util.List;
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
@JsonDeserialize(using = ServiceNowImportSetResponseNGDeserializer.class)
public class ServiceNowImportSetResponseNG {
  @JsonProperty("import_set") @NotNull String importSet = "";
  @JsonProperty("staging_table") @NotNull String stagingTable = "";
  @JsonProperty("transformation_result")
  @NotNull
  List<ServiceNowImportSetTransformMapResult> serviceNowImportSetTransformMapResultList = new ArrayList<>();

  public ServiceNowImportSetResponseNG(JsonNode node) {
    this.importSet = JsonNodeUtils.mustGetString(node, "import_set");
    this.stagingTable = JsonNodeUtils.mustGetString(node, "staging_table");
    List<ServiceNowImportSetTransformMapResult> transformMapResultList = new ArrayList<>();
    JsonNode transformMapResultBody = node.get("transformation_result");
    for (final JsonNode transformMapBody : transformMapResultBody) {
      transformMapResultList.add(new ServiceNowImportSetTransformMapResult(transformMapBody));
    }
    this.serviceNowImportSetTransformMapResultList = transformMapResultList;
  }
}
