/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eventsframework;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.logging.AutoLogContext;

import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.PL)
public class EntityChangeLogContext extends AutoLogContext {
  public EntityChangeLogContext(EntityChangeDTO entityChangeDTO) {
    super(buildLogContext(entityChangeDTO), OverrideBehavior.OVERRIDE_NESTS);
  }

  private static Map<String, String> buildLogContext(EntityChangeDTO entityChangeDTO) {
    Map<String, String> logContext = new HashMap<>();
    logContext.put("accountId", entityChangeDTO.getAccountIdentifier().getValue());
    logContext.put("orgIdentifier", entityChangeDTO.getOrgIdentifier().getValue());
    logContext.put("projectIdentifier", entityChangeDTO.getProjectIdentifier().getValue());
    logContext.put("identifier", entityChangeDTO.getIdentifier().getValue());
    return logContext;
  }
}
