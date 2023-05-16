/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.encoding.EncodingUtils.deCompressString;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.GoogleDataStoreAware.readBlob;
import static io.harness.persistence.GoogleDataStoreAware.readLong;
import static io.harness.persistence.GoogleDataStoreAware.readString;

import static software.wings.beans.Log.Builder.aLog;

import static java.lang.System.currentTimeMillis;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.dataretention.AccountDataRetentionEntity;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.GoogleDataStoreAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.BlobValue;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.StringValue;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@FieldNameConstants(innerTypeName = "LogKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "commandLogs", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(CDC)
@TargetModule(_957_CG_BEANS)
public class Log implements GoogleDataStoreAware, PersistentEntity, AccountDataRetentionEntity, UuidAware,
                            CreatedAtAware, CreatedByAware, UpdatedAtAware, UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("appId_activityId")
                 .field(LogKeys.appId)
                 .field(LogKeys.activityId)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("activityIdCreatedAt")
                 .field(LogKeys.activityId)
                 .ascSortField(LogKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_validUntil")
                 .field(LogKeys.accountId)
                 .field(LogKeys.validUntil)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @NotNull @SchemaIgnore protected String appId;
  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  @NotEmpty private String activityId;
  private String hostName;
  @NotEmpty private String commandUnitName;
  private String logLine;
  private int linesCount;
  @NotNull private LogLevel logLevel;
  @NotNull private CommandExecutionStatus commandExecutionStatus;
  @FdIndex private String accountId;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(6).toInstant());

  @Override
  public com.google.cloud.datastore.Entity convertToCloudStorageEntity(Datastore datastore) {
    Key taskKey = datastore.newKeyFactory()
                      .setKind(Log.class.getAnnotation(dev.morphia.annotations.Entity.class).value())
                      .newKey(generateUuid());
    try {
      com.google.cloud.datastore.Entity.Builder logEntityBuilder =
          com.google.cloud.datastore.Entity.newBuilder(taskKey)
              .set(LogKeys.activityId, getActivityId())
              .set(LogKeys.linesCount, LongValue.newBuilder(getLinesCount()).setExcludeFromIndexes(true).build())
              .set(LogKeys.logLevel,
                  com.google.cloud.datastore.StringValue.newBuilder(getLogLevel().toString())
                      .setExcludeFromIndexes(true)
                      .build())
              .set(LogKeys.commandExecutionStatus,
                  com.google.cloud.datastore.StringValue.newBuilder(getCommandExecutionStatus().name())
                      .setExcludeFromIndexes(true)
                      .build())
              .set(LogKeys.createdAt, currentTimeMillis());

      if (getLogLine().length() <= 256) {
        logEntityBuilder.set(LogKeys.logLine, StringValue.newBuilder(getLogLine()).setExcludeFromIndexes(true).build());
      } else {
        logEntityBuilder.set(LogKeys.compressedLogLine,
            BlobValue.newBuilder(Blob.copyFrom(compressString(getLogLine()))).setExcludeFromIndexes(true).build());
      }

      if (isNotEmpty(getHostName())) {
        logEntityBuilder.set(LogKeys.hostName, getHostName());
      }

      if (isNotEmpty(getAppId())) {
        logEntityBuilder.set(LogKeys.appId, getAppId());
      }

      if (isNotEmpty(getCommandUnitName())) {
        logEntityBuilder.set(LogKeys.commandUnitName, getCommandUnitName());
      }

      if (validUntil != null) {
        logEntityBuilder.set(LogKeys.validUntil, validUntil.getTime());
      }

      if (isNotEmpty(getAccountId())) {
        logEntityBuilder.set(LogKeys.accountId, getAccountId());
      }

      return logEntityBuilder.build();
    } catch (IOException e) {
      throw new GeneralException("Cannot convert log object", e);
    }
  }

  @Override
  public GoogleDataStoreAware readFromCloudStorageEntity(com.google.cloud.datastore.Entity entity) {
    final Log logObject = aLog()
                              .uuid(entity.getKey().getName())
                              .activityId(readString(entity, LogKeys.activityId))
                              .logLevel(LogLevel.valueOf(readString(entity, LogKeys.logLevel)))
                              .createdAt(readLong(entity, LogKeys.createdAt))
                              .hostName(readString(entity, LogKeys.hostName))
                              .appId(readString(entity, LogKeys.appId))
                              .commandUnitName(readString(entity, LogKeys.commandUnitName))
                              .accountId(readString(entity, LogKeys.accountId))
                              .build();
    try {
      byte[] compressedLogLine = readBlob(entity, LogKeys.compressedLogLine);
      if (isNotEmpty(compressedLogLine)) {
        logObject.setLogLine(deCompressString(compressedLogLine));
      } else {
        logObject.setLogLine(readString(entity, LogKeys.logLine));
      }
    } catch (IOException e) {
      throw new WingsException(e);
    }

    return logObject;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String activityId;
    private String hostName;
    private String commandUnitName;
    private String logLine;
    private LogLevel logLevel;
    private CommandExecutionStatus commandExecutionStatus;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private String accountId;

    private Builder() {}

    /**
     * A log builder.
     *
     * @return the builder
     */
    public static Builder aLog() {
      return new Builder();
    }

    /**
     * With activity id builder.
     *
     * @param activityId the activity id
     * @return the builder
     */
    public Builder activityId(String activityId) {
      this.activityId = activityId;
      return this;
    }

    /**
     * With host name builder.
     *
     * @param hostName the host name
     * @return the builder
     */
    public Builder hostName(String hostName) {
      this.hostName = hostName;
      return this;
    }

    /**
     * With command unit name builder.
     *
     * @param commandUnitName the command unit name
     * @return the builder
     */
    public Builder commandUnitName(String commandUnitName) {
      this.commandUnitName = commandUnitName;
      return this;
    }

    /**
     * With log line builder.
     *
     * @param logLine the log line
     * @return the builder
     */
    public Builder logLine(String logLine) {
      this.logLine = logLine;
      return this;
    }

    /**
     * With log level builder.
     *
     * @param logLevel the log level
     * @return the builder
     */
    public Builder logLevel(LogLevel logLevel) {
      this.logLevel = logLevel;
      return this;
    }

    /**
     * With execution result builder.
     *
     * @param commandExecutionStatus the execution result
     * @return the builder
     */
    public Builder executionResult(CommandExecutionStatus commandExecutionStatus) {
      this.commandExecutionStatus = commandExecutionStatus;
      return this;
    }

    /**
     * With uuid builder.
     *
     * @param uuid the uuid
     * @return the builder
     */
    public Builder uuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With app id builder.
     *
     * @param appId the app id
     * @return the builder
     */
    public Builder appId(String appId) {
      this.appId = appId;
      return this;
    }

    /**
     * With created by builder.
     *
     * @param createdBy the created by
     * @return the builder
     */
    public Builder createdBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With created at builder.
     *
     * @param createdAt the created at
     * @return the builder
     */
    public Builder createdAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * With last updated by builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the builder
     */
    public Builder lastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the builder
     */
    public Builder lastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param accountId accountId
     * @return the builder
     */
    public Builder accountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aLog()
          .activityId(activityId)
          .hostName(hostName)
          .commandUnitName(commandUnitName)
          .logLine(logLine)
          .logLevel(logLevel)
          .executionResult(commandExecutionStatus)
          .uuid(uuid)
          .appId(appId)
          .createdBy(createdBy)
          .createdAt(createdAt)
          .lastUpdatedBy(lastUpdatedBy)
          .lastUpdatedAt(lastUpdatedAt)
          .accountId(accountId);
    }

    /**
     * Build log.
     *
     * @return the log
     */
    public Log build() {
      Log logObject = new Log();
      logObject.setActivityId(activityId);
      logObject.setHostName(hostName);
      logObject.setCommandUnitName(commandUnitName);
      logObject.setLogLine(logLine);
      logObject.setLogLevel(logLevel);
      logObject.setCommandExecutionStatus(commandExecutionStatus);
      logObject.setUuid(uuid);
      logObject.setAppId(appId);
      logObject.setCreatedBy(createdBy);
      logObject.setCreatedAt(createdAt);
      logObject.setLastUpdatedBy(lastUpdatedBy);
      logObject.setLastUpdatedAt(lastUpdatedAt);
      logObject.setAccountId(accountId);
      return logObject;
    }
  }

  @UtilityClass
  public static final class LogKeys {
    public static final String compressedLogLine = "compressedLogLine";
  }

  public static Log fromDTO(software.wings.beans.dto.Log log) {
    return aLog()
        .uuid(log.getUuid())
        .appId(log.getAppId())
        .createdBy(log.getCreatedBy())
        .createdAt(log.getCreatedAt())
        .lastUpdatedBy(log.getLastUpdatedBy())
        .lastUpdatedAt(log.getLastUpdatedAt())
        .activityId(log.getActivityId())
        .hostName(log.getHostName())
        .commandUnitName(log.getCommandUnitName())
        .logLine(log.getLogLine())
        .logLevel(log.getLogLevel())
        .executionResult(log.getCommandExecutionStatus())
        .accountId(log.getAccountId())
        .build();
  }
}
