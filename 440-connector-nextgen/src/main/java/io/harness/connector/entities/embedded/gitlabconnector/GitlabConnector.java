/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.gitlabconnector;

import io.harness.annotations.StoreIn;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.ng.DbAliases;

import dev.morphia.annotations.Entity;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "GitlabConnectorKeys")
@Persistent
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.gitlabconnector.GitlabConnector")
public class GitlabConnector extends Connector implements PersistentRegularIterable {
  GitConnectionType connectionType;
  String url;
  String validationRepo;
  GitAuthType authType;
  GitlabAuthentication authenticationDetails;
  boolean hasApiAccess;
  GitlabApiAccessType apiAccessType;
  GitlabApiAccess gitlabApiAccess;
  @NonFinal Long nextTokenRenewIteration;

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (GitlabConnectorKeys.nextTokenRenewIteration.equals(fieldName)) {
      return nextTokenRenewIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (GitlabConnectorKeys.nextTokenRenewIteration.equals(fieldName)) {
      this.nextTokenRenewIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }
}
