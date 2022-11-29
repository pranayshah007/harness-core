/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.EntityVersionCollection;
import software.wings.service.intfc.ownership.OwnedByAccount;

/**
 * Created by rishi on 10/18/16.
 */
public interface EntityVersionService extends OwnedByAccount {
  PageResponse<EntityVersionCollection> listEntityVersions(PageRequest<EntityVersionCollection> pageRequest);

  EntityVersion lastEntityVersion(String appId, EntityType entityType, String entityUuid);

  EntityVersion lastEntityVersion(String appId, EntityType entityType, String entityUuid, String parentUuid);

  EntityVersion newEntityVersion(
      String appId, EntityType entityType, String entityUuid, String name, ChangeType changeType);

  EntityVersion newEntityVersion(
      String appId, EntityType entityType, String entityUuid, String name, ChangeType changeType, String entityData);

  EntityVersion newEntityVersion(String appId, EntityType entityType, String entityUuid, String parentUuid, String name,
      ChangeType changeType, String entityData);

  void updateEntityData(String appId, String entityVersionUuid, String entityData);
}
