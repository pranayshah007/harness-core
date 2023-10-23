/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.servicenow;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@FieldNameConstants(innerTypeName = "ServiceNowConnectorKeys")
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.servicenow.ServiceNowConnector")
public class ServiceNowConnector extends Connector {
  String serviceNowUrl;
  /** @deprecated */
  @Deprecated(since = "moved to ServiceNowConnector with authType and serviceNowAuthentication") String username;
  /** @deprecated */
  @Deprecated(since = "moved to ServiceNowConnector with authType and serviceNowAuthentication") String usernameRef;
  /** @deprecated */
  @Deprecated(since = "moved to ServiceNowConnector with authType and serviceNowAuthentication") String passwordRef;
  @NotEmpty ServiceNowAuthType authType;
  @NotNull ServiceNowAuthentication serviceNowAuthentication;
}
