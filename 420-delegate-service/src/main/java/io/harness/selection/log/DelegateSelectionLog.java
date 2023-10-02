/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.selection.log;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.selection.log.DelegateMetaData.DelegateMetaDataKeys;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.StringValue;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateSelectionLogRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "DelegateSelectionLogKeys")
public class DelegateSelectionLog
    implements PersistentEntity, UuidAware, AccountAccess, GoogleDataStoreAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .field(DelegateSelectionLogKeys.accountId)
                 .field(DelegateSelectionLogKeys.taskId)
                 .field(DelegateSelectionLogKeys.message)
                 .name("selectionLogs")
                 .build())
        .build();
  }
  public static final String DELEGATE_NAME =
      DelegateSelectionLogKeys.delegateMetaData + "." + DelegateMetaDataKeys.delegateName;
  public static final String HOST_NAME =
      DelegateSelectionLogKeys.delegateMetaData + "." + DelegateMetaDataKeys.hostName;
  public static final String DELEGATE_ID =
      DelegateSelectionLogKeys.delegateMetaData + "." + DelegateMetaDataKeys.delegateId;

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @NotEmpty private String accountId;
  @NotEmpty private Set<String> delegateIds;
  @NotEmpty private String taskId;
  @NotEmpty private String message;
  @NotEmpty private String conclusion;
  @NotEmpty private long eventTimestamp;
  private DelegateMetaData delegateMetaData;
  private long createdAt;
  /*
   * Used for deduplication of logs. Standalone logs will have a unique value and groups will have fixed.
   * */

  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(DelegateSelectionLog.class.getAnnotation(dev.morphia.annotations.Entity.class).value())
                      .newKey(generateUuid());
    com.google.cloud.datastore.Entity.Builder logEntityBuilder =
        com.google.cloud.datastore.Entity.newBuilder(taskKey)
            .set(DelegateSelectionLogKeys.accountId, accountId)
            .set(DelegateSelectionLogKeys.taskId, taskId)
            .set(DelegateSelectionLogKeys.message, StringValue.newBuilder(message).setExcludeFromIndexes(true).build())
            .set(DelegateSelectionLogKeys.conclusion,
                StringValue.newBuilder(conclusion).setExcludeFromIndexes(true).build())
            .set(DelegateSelectionLogKeys.eventTimestamp,
                LongValue.newBuilder(eventTimestamp).setExcludeFromIndexes(true).build())
            .set(DelegateSelectionLogKeys.validUntil, validUntil.getTime());

    if (delegateMetaData != null) {
      logEntityBuilder.set(DELEGATE_NAME, delegateMetaData.getDelegateName());
      logEntityBuilder.set(DELEGATE_ID, delegateMetaData.getDelegateId());
      logEntityBuilder.set(HOST_NAME, delegateMetaData.getHostName());
    }

    if (isNotEmpty(delegateIds)) {
      logEntityBuilder.set(DelegateSelectionLogKeys.delegateIds, delegateIds.stream().collect(Collectors.joining(",")));
    }
    return logEntityBuilder.build();
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final DelegateSelectionLog delegateSelectionLog =
        DelegateSelectionLog.builder()
            .uuid(entity.getKey().getName())
            .accountId(readString(entity, DelegateSelectionLogKeys.accountId))
            .taskId(readString(entity, DelegateSelectionLogKeys.taskId))
            .message(readString(entity, DelegateSelectionLogKeys.message))
            .conclusion(readString(entity, DelegateSelectionLogKeys.conclusion))
            .eventTimestamp(readLong(entity, DelegateSelectionLogKeys.eventTimestamp))
            .delegateMetaData(DelegateMetaData.builder()
                                  .delegateId(readString(entity, DELEGATE_ID))
                                  .delegateName(readString(entity, DELEGATE_NAME))
                                  .hostName(readString(entity, HOST_NAME))
                                  .build())

            .build();

    String delegateIdsString = readString(entity, DelegateSelectionLogKeys.delegateIds);
    if (isNotEmpty(delegateIdsString)) {
      delegateSelectionLog.setDelegateIds(new HashSet<>(Arrays.asList(delegateIdsString.split(","))));
    }
    return delegateSelectionLog;
  }
}
