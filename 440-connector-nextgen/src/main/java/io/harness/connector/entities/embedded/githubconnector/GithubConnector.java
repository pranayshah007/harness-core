/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.githubconnector;

import io.harness.annotations.StoreIn;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "GithubConnectorKeys")
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.githubconnector.GithubConnector")
@Persistent
public class GithubConnector extends Connector {
  GitConnectionType connectionType;
  String url;
  String validationRepo;
  GitAuthType authType;
  GithubAuthentication authenticationDetails;
  boolean hasApiAccess;
  GithubApiAccessType apiAccessType;
  GithubApiAccess githubApiAccess;
}
