/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * The current version
 * of a search entity.
 *
 * @author utkarsh
 */
@OwnedBy(PL)
@Value
@StoreIn(DbAliases.HARNESS)
@Entity(value = "searchEntitiesIndexState", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "SearchEntityIndexStateKeys")
@Slf4j
public class SearchEntityIndexState implements PersistentEntity {
  @Id private String entityClass;
  private String syncVersion;
  private String indexName;
  private boolean recreateIndex;

  private SearchEntity getSearchEntity() {
    try {
      return (SearchEntity) Class.forName(entityClass).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      log.error("Could not create new instance", e);
    }
    return null;
  }

  boolean shouldBulkSync() {
    SearchEntity searchEntity = getSearchEntity();
    if (searchEntity != null) {
      return !(searchEntity.getVersion().equals(syncVersion)) || recreateIndex;
    }
    return true;
  }
}
