/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.filter.FilterConstants.CONNECTOR_FILTER;

import io.harness.ConnectorConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.CcmConnectorFilter;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(CONNECTOR_FILTER)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("ConnectorInternalFilterProperties")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CE)
@Schema(name = "ConnectorInternalFilterProperties",
    description = "Properties of the Connector Internal Filter defined in Harness")
public class ConnectorInternalFilterPropertiesDTO extends FilterPropertiesDTO {
  @Schema(description = ConnectorConstants.CONNECTOR_TYPE_LIST) List<ConnectorType> types;
  @Schema(description = ConnectorConstants.CONNECTOR_STATUS_LIST) List<ConnectivityStatus> connectivityStatuses;
  @Schema(description = "CCM Connector filter.") CcmConnectorFilter ccmConnectorFilter;
  @Schema(description = "List of account identifiers", required = true) List<String> accountIdentifiers;
  @Override
  @Schema(type = "string", allowableValues = {"Connector"})
  public FilterType getFilterType() {
    return FilterType.CONNECTOR;
  }
}
