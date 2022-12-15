/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readBoolean;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * @author Praveen
 */
@Data
@Builder
@FieldNameConstants(innerTypeName = "SupervisedTrainingStatusKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "supervisedTrainingStatus", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class SupervisedTrainingStatus implements GoogleDataStoreAware, CreatedAtAware, UuidAware {
  private String serviceId;
  private boolean isEmbeddingReady;
  private boolean isSupervisedReady;
  @Id String uuid;
  private long createdAt;

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(this.uuid == null ? generateUuid() : this.uuid);

    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, SupervisedTrainingStatusKeys.serviceId, serviceId, false);
    addFieldIfNotEmpty(recordBuilder, SupervisedTrainingStatusKeys.isEmbeddingReady, isEmbeddingReady, true);
    addFieldIfNotEmpty(recordBuilder, SupervisedTrainingStatusKeys.isSupervisedReady, isSupervisedReady, true);
    addFieldIfNotEmpty(recordBuilder, SupervisedTrainingStatusKeys.createdAt, createdAt, true);
    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    return SupervisedTrainingStatus.builder()
        .uuid(entity.getKey().getName())
        .serviceId(readString(entity, SupervisedTrainingStatusKeys.serviceId))
        .isSupervisedReady(readBoolean(entity, SupervisedTrainingStatusKeys.isSupervisedReady))
        .isEmbeddingReady(readBoolean(entity, SupervisedTrainingStatusKeys.isEmbeddingReady))
        .createdAt(readLong(entity, SupervisedTrainingStatusKeys.createdAt))
        .build();
  }
}
