/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.awscodecommitconnector;

import io.harness.annotations.StoreIn;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
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
@FieldNameConstants(innerTypeName = "AwsCodeCommitConfigKeys")
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.ConnectorDisconnectHandler.entities.embedded.awscodecommitconnector.AwsCodeCommitConfig")
public class AwsCodeCommitConfig extends Connector {
  AwsCodeCommitUrlType urlType;
  String url;
  AwsCodeCommitAuthentication authentication;
}
