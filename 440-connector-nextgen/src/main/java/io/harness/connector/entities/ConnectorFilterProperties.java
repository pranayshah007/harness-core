/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities;

import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorCategory;
import io.harness.connector.ConnectorConnectivityMode;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.filter.entity.FilterProperties;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.ConnectorFilterProperties")
public class ConnectorFilterProperties extends FilterProperties {
  List<String> connectorNames;
  List<String> connectorIdentifiers;
  String description;
  List<ConnectorType> types;
  List<ConnectorCategory> categories;
  List<ConnectivityStatus> connectivityStatuses;
  Boolean inheritingCredentialsFromDelegate;
  List<ConnectorConnectivityMode> connectorConnectivityModes;
}
