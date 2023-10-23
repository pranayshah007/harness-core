/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.prometheusconnector;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.customhealthconnector.CustomHealthConnectorKeyAndValue;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "PrometheusConnectorKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.prometheusConnector.PrometheusConnector")
@OwnedBy(HarnessTeam.CV)
public class PrometheusConnector extends Connector {
  private String url;
  List<CustomHealthConnectorKeyAndValue> headers;
  String username;
  String passwordRef;

  public List<CustomHealthConnectorKeyAndValue> getHeaders() {
    if (headers == null) {
      return Collections.emptyList();
    }
    return headers;
  }
}
