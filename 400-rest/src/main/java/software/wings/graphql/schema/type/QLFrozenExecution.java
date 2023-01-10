/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type;

import static io.harness.annotations.dev.HarnessTeam.SPG;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@OwnedBy(SPG)
@Value
@Builder
@Scope(ResourceType.APPLICATION)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLFrozenExecution implements QLObject {
  QLExecution execution;
  List<QLEnvSummary> environments;
  List<String> serviceIds;
  List<String> rejectedByFreezeWindowIds;
  List<String> rejectedByFreezeWindowNames;
  String pipelineExecutionId;
}
