/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.customsecretmanager;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.ng.DbAliases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Value
@Builder
@ToString(exclude = {"connectorRef"})
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "CustomSecretManagerConnectorKeys")
@JsonInclude(JsonInclude.Include.NON_NULL)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.customsecretmanager.CustomSecretManagerConnector")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomSecretManagerConnector extends Connector {
  @Builder.Default private Boolean isDefault = false;
  private String connectorRef;
  private String host;
  private String workingDirectory;
  private TemplateLinkConfigForCustomSecretManager template;
  @Builder.Default private Boolean onDelegate = Boolean.FALSE;
}
