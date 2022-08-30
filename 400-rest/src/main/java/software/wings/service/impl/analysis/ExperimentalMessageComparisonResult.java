/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.addFieldIfNotEmpty;
import static io.harness.persistence.GoogleDataStoreAware.readDouble;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.FdIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ExperimentalMessageComparisonResultKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "experimentalLogMessageComparisonResult")
@HarnessEntity(exportable = false)
public class ExperimentalMessageComparisonResult
    implements PersistentEntity, UuidAware, GoogleDataStoreAware, CreatedAtAware {
  @NotEmpty @FdIndex private String stateExecutionId;
  @FdIndex private String cvConfigId;
  @NotEmpty private int logCollectionMinute;
  int numVotes;
  private String message1;
  private String message2;
  private String prediction;
  @JsonProperty("cluster_type") private String clusterType;
  private double similarity;
  private String modelVersion;
  private Map<String, String> userVotes;

  @Id @SchemaIgnore private String uuid;

  private long createdAt;

  public void incrementNumVotes() {
    this.numVotes++;
  }
  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(this.getClass().getAnnotation(org.mongodb.morphia.annotations.Entity.class).value())
                      .newKey(this.getUuid() == null ? generateUuid() : this.getUuid());
    com.google.cloud.datastore.Entity.Builder recordBuilder = com.google.cloud.datastore.Entity.newBuilder(taskKey);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.cvConfigId, cvConfigId, false);
    addFieldIfNotEmpty(
        recordBuilder, ExperimentalMessageComparisonResultKeys.logCollectionMinute, logCollectionMinute, false);
    addFieldIfNotEmpty(
        recordBuilder, ExperimentalMessageComparisonResultKeys.stateExecutionId, stateExecutionId, false);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.numVotes, numVotes, false);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.message1, message1, true);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.message2, message2, true);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.prediction, prediction, true);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.clusterType, clusterType, true);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.similarity, similarity, true);
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.modelVersion, modelVersion, false);
    addFieldIfNotEmpty(
        recordBuilder, ExperimentalMessageComparisonResultKeys.userVotes, JsonUtils.asJson(userVotes), true);
    if (createdAt == 0) {
      createdAt = System.currentTimeMillis();
    }
    addFieldIfNotEmpty(recordBuilder, ExperimentalMessageComparisonResultKeys.createdAt, createdAt, true);

    return recordBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final ExperimentalMessageComparisonResult result =
        ExperimentalMessageComparisonResult.builder()
            .createdAt(readLong(entity, ExperimentalMessageComparisonResultKeys.createdAt))
            .cvConfigId(readString(entity, ExperimentalMessageComparisonResultKeys.cvConfigId))
            .stateExecutionId(readString(entity, ExperimentalMessageComparisonResultKeys.stateExecutionId))
            .logCollectionMinute((int) readLong(entity, ExperimentalMessageComparisonResultKeys.logCollectionMinute))
            .message1(readString(entity, ExperimentalMessageComparisonResultKeys.message1))
            .message2(readString(entity, ExperimentalMessageComparisonResultKeys.message2))
            .prediction(readString(entity, ExperimentalMessageComparisonResultKeys.prediction))
            .similarity(readDouble(entity, ExperimentalMessageComparisonResultKeys.similarity))
            .modelVersion(readString(entity, ExperimentalMessageComparisonResultKeys.modelVersion))
            .clusterType(readString(entity, ExperimentalMessageComparisonResultKeys.clusterType))
            .numVotes((int) readLong(entity, ExperimentalMessageComparisonResultKeys.numVotes))
            .uuid(entity.getKey().getName())
            .build();

    String votes = readString(entity, ExperimentalMessageComparisonResultKeys.userVotes);
    Map<String, String> userVote = JsonUtils.asObject(votes, Map.class);
    result.setUserVotes(userVote);
    return result;
  }
}
