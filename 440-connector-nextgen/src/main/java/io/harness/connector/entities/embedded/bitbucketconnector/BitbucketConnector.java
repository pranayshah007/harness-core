/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.bitbucketconnector;

import io.harness.connector.entities.Connector;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;

import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccess;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "BitbucketConnectorKeys")
@EqualsAndHashCode(callSuper = true)
@Entity(value = "connectors", noClassnameStored = true)
@TypeAlias("io.harness.connector.entities.embedded.bitbucketconnector.BitbucketConnector")
@Persistent
public class BitbucketConnector extends Connector {
  GitConnectionType connectionType;
  String url;
  String validationRepo;
  GitAuthType authType;
  BitbucketAuthentication authenticationDetails;
  boolean hasApiAccess;
  BitbucketApiAccessType apiAccessType;
  BitbucketApiAccess bitbucketApiAccess;
  @NonFinal
  Long nextTokenRenewIteration;
}
