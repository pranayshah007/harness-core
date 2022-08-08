/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.schema.type.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateScope;

import software.wings.beans.DelegateConnection;
import software.wings.graphql.schema.type.QLObject;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@OwnedBy(DEL)
@Value
@Builder
@AllArgsConstructor
@Scope(PermissionAttribute.ResourceType.APPLICATION)
public class QLDelegate implements QLObject {
  String uuid;
  String accountId;
  String delegateType;
  String delegateName;
  String hostName;
  String description;
  String ip;
  boolean pollingModeEnabled;
  String status;
  long lastHeartBeat;
  Long lastHeartbeat;
  String version;
  String delegateProfileId;
  List<String> tags;
  List<DelegateConnection> connections;
  List<DelegateScope> includeScopes;
  List<DelegateScope> excludeScopes;
  List<String> supportedTasks;
}
