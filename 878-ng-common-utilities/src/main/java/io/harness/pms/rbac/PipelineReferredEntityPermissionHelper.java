/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.rbac;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.groovy.util.Maps;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineReferredEntityPermissionHelper {
  private final String PERMISSION_PLACE_HOLDER = "core_%s_%s";

  public final Map<EntityType, String> coreEntityTypeToPermissionEntityName = Maps.of(EntityType.CONNECTORS,
      NGResourceType.CONNECTOR, EntityType.SECRETS, NGResourceType.SECRETS, EntityType.SERVICE, NGResourceType.SERVICE,
      EntityType.ENVIRONMENT, NGResourceType.ENVIRONMENT, EntityType.FILES, NGResourceType.FILE);

  public String getPermissionForGivenType(EntityType entityType, boolean isNew) {
    String permission = "access";
    if (isNew) {
      permission = "edit";
    }
    if (coreEntityTypeToPermissionEntityName.containsKey(entityType)) {
      return String.format(
          PERMISSION_PLACE_HOLDER, coreEntityTypeToPermissionEntityName.get(entityType).toLowerCase(), permission);
    }
    throw new UnsupportedOperationException();
  }

  public String getEntityName(EntityType entityType) {
    return coreEntityTypeToPermissionEntityName.get(entityType);
  }
}
