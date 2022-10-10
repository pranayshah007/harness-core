/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.queue.Queuable;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

@OwnedBy(CDC)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "executionQueue", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ExecutionEvent extends Queuable {
  private String appId;
  private String workflowId;
  private List<String> infraMappingIds;
  private List<String> infraDefinitionIds;
}
