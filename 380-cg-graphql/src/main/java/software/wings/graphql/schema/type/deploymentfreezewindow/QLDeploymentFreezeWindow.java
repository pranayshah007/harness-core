/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.deploymentfreezewindow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.graphql.schema.mutation.deploymentfreezewindow.input.QLUserGroupFilterInput;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.Scope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@Scope(PermissionAttribute.ResourceType.USER)
@JsonIgnoreProperties(ignoreUnknown = true)
public class QLDeploymentFreezeWindow {
  String id;
  String name;
  String description;
  Boolean applicable;
  List<String> notifyTo;
  List<QLFreezeWindow> freezeWindows;
  QLSetup setup;
  QLUserGroupFilterInput userGroupSelection;
}
