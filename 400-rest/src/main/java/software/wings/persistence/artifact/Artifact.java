/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.persistence.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.expression.LateBindingMap;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.entityinterface.ApplicationAccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.annotations.Transient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@FieldNameConstants(innerTypeName = "ArtifactKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "artifacts", noClassnameStored = true)
@HarnessEntity(exportable = true)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Artifact implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                 UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("owners")
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.appId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("artifactStream_buildNo")
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.metadata_buildNo)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("artifactStream_createdAt_buildNo")
                 .field(ArtifactKeys.artifactStreamId)
                 .descSortField(ArtifactKeys.createdAt)
                 .rangeField(ArtifactKeys.metadata_buildNo)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("artifactStream_artifactPath")
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.metadata_artifactPath)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("artifactStream_revision")
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.revision)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_artifactStreamId_revision_createdAt")
                 .field(ArtifactKeys.accountId)
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.metadata_buildNo)
                 .descSortField(ArtifactKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("appId_artifactStreamId_status_createdAt")
                 .field(ArtifactKeys.appId)
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.status)
                 .descSortField(ArtifactKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_artifactStreamId_createdAt")
                 .field(ArtifactKeys.accountId)
                 .field(ArtifactKeys.artifactStreamId)
                 .descSortField(ArtifactKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("appId_artifactStreamId_metadata_image")
                 .field(ArtifactKeys.appId)
                 .field(ArtifactKeys.artifactStreamId)
                 .field(ArtifactKeys.metadata_image)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_artifactStreamIds")
                 .field(ArtifactKeys.accountId)
                 .descSortField(ArtifactKeys.createdAt)
                 .rangeField(ArtifactKeys.artifactStreamId)
                 .build())
        .build();
  }

  @UtilityClass
  public static final class ArtifactKeys {
    public static final String uuid = "uuid";
    public static final String appId = "appId";
    public static final String createdAt = "createdAt";
    public static final String metadata_image = metadata + "." + ArtifactMetadataKeys.image;
    public static final String metadata_tag = metadata + "." + ArtifactMetadataKeys.tag;
    public static final String metadata_buildNo = metadata + "." + ArtifactMetadataKeys.buildNo;
    public static final String metadata_artifactPath = metadata + "." + ArtifactMetadataKeys.artifactPath;
  }

  @EqualsAndHashCode.Exclude @Deprecated public static final String ID_KEY2 = "_id";
  @EqualsAndHashCode.Exclude @Deprecated public static final String APP_ID_KEY2 = "appId";
  @EqualsAndHashCode.Exclude @Deprecated public static final String ACCOUNT_ID_KEY2 = "accountId";
  @EqualsAndHashCode.Exclude @Deprecated public static final String LAST_UPDATED_AT_KEY2 = "lastUpdatedAt";

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @NotNull @SchemaIgnore protected String appId;
  @EqualsAndHashCode.Exclude @SchemaIgnore private EmbeddedUser createdBy;
  @EqualsAndHashCode.Exclude @SchemaIgnore @FdIndex private long createdAt;

  @EqualsAndHashCode.Exclude @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @EqualsAndHashCode.Exclude @SchemaIgnore @NotNull private long lastUpdatedAt;

  /**
   * TODO: Add isDeleted boolean field to enable soft delete. @swagat
   */

  @JsonIgnore
  @SchemaIgnore
  @Transient
  private transient String entityYamlPath; // TODO:: remove it with changeSet batching

  @JsonIgnore
  @SchemaIgnore
  public String getEntityYamlPath() {
    return entityYamlPath;
  }

  @EqualsAndHashCode.Exclude @Setter @JsonIgnore @SchemaIgnore private transient boolean syncFromGit;

  @JsonIgnore
  @SchemaIgnore
  public boolean isSyncFromGit() {
    return syncFromGit;
  }
  private String artifactStreamId;
  private String artifactSourceName;
  @Transient private String artifactStreamName;
  private ArtifactMetadata metadata = new ArtifactMetadata(new HashMap<>());
  @Transient @JsonIgnore public LateBindingMap label;
  @NotEmpty private String displayName;
  private String revision;
  private List<String> serviceIds = new ArrayList<>();
  private List<ArtifactFile> artifactFiles = Lists.newArrayList();
  private List<ArtifactFileMetadata> artifactFileMetadata = Lists.newArrayList();
  private Status status;
  private String description;
  private String errorMessage;
  private ContentStatus contentStatus;
  private Map<String, String> source;
  private String settingId;
  private String accountId;
  private String artifactStreamType;
  private String uiDisplayName;
  private String buildIdentity;
  @Transient @JsonIgnore private boolean isDuplicate;

  /**
   * Gets buildNo.
   *
   * @return the buildNo
   */
  public String getBuildNo() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.buildNo);
    }
    return null;
  }

  /**
   * Gets Artifact Path
   *
   * @return the buildNo
   */
  public String getArtifactPath() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.artifactPath);
    }
    return null;
  }

  /**
   * Gets Bucket Name
   *
   * @return the bucket name
   */
  public String getBucketName() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.bucketName);
    }
    return null;
  }

  /**
   * Gets Key
   *
   * @return the bucket name
   */
  public String getKey() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.key);
    }
    return null;
  }

  /**
   * Gets Key
   *
   * @return the bucket name
   */
  public String getUrl() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.url);
    }
    return null;
  }

  /**
   * Gets artifact file Name
   * @return
   */
  public String getFileName() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.artifactFileName);
    }
    return null;
  }

  // TODO: ASR: IMP: remove this method after refactor
  public String fetchAppId() {
    return appId;
  }

  /**
   * Gets Full build display name
   * @return
   */
  public String getBuildFullDisplayName() {
    if (getMetadata() != null) {
      return getMetadata().get(ArtifactMetadataKeys.buildFullDisplayName);
    }
    return null;
  }

  /**
   * Gets Artifact file size
   *
   * @return
   */
  public Long getArtifactFileSize() {
    if (getMetadata() != null && getMetadata().get(ArtifactMetadataKeys.artifactFileSize) != null) {
      return Long.valueOf(getMetadata().get(ArtifactMetadataKeys.artifactFileSize));
    }
    return null;
  }

  public String getUiDisplayName() {
    if (EmptyPredicate.isNotEmpty(uiDisplayName)) {
      return uiDisplayName;
    }
    return "Build# " + getBuildNo();
  }

  public List<ArtifactFileMetadata> getArtifactFileMetadata() {
    return artifactFileMetadata;
  }
  /**
   * The Enum Status.
   */
  public enum Status {
    /**
     * New status.
     */
    NEW {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.NEW;
      }
    },
    /**
     * Running status.
     */
    RUNNING {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.RUNNING;
      }
    },
    /**
     * Queued status.
     */
    QUEUED {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.QUEUED;
      }
    },
    /**
     * Waiting status.
     */
    WAITING {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.WAITING;
      }
    },
    /**
     * Ready status.
     */
    READY(true) {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.READY;
      }
    },
    /**
     * Aborted status.
     */
    APPROVED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.APPROVED;
      }
    },
    /**
     * Rejected status.
     */
    REJECTED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.REJECTED;
      }
    },
    /**
     * Aborted status.
     */
    ABORTED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.ABORTED;
      }
    },
    /**
     * Failed status.
     */
    FAILED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.FAILED;
      }
    },
    /**
     * Error status.
     */
    ERROR(true) {
      @Override
      public software.wings.beans.artifact.Artifact.Status toDTO() {
        return software.wings.beans.artifact.Artifact.Status.ERROR;
      }
    };

    Status() {}

    Status(boolean finalStatus) {
      this.finalStatus = finalStatus;
    }

    public boolean isFinalStatus() {
      return finalStatus;
    }

    private boolean finalStatus;

    public abstract software.wings.beans.artifact.Artifact.Status toDTO();
  }

  /**
   * The Enum Status.
   */
  public enum ContentStatus {
    /***
     * METADATA ONLY
     */
    METADATA_ONLY {
      @Override
      public software.wings.beans.artifact.Artifact.ContentStatus toDTO() {
        return software.wings.beans.artifact.Artifact.ContentStatus.METADATA_ONLY;
      }
    },
    /**
     * New status.
     */
    NOT_DOWNLOADED {
      @Override
      public software.wings.beans.artifact.Artifact.ContentStatus toDTO() {
        return software.wings.beans.artifact.Artifact.ContentStatus.NOT_DOWNLOADED;
      }
    },
    /**
     * Downloading status.
     */
    DOWNLOADING {
      @Override
      public software.wings.beans.artifact.Artifact.ContentStatus toDTO() {
        return software.wings.beans.artifact.Artifact.ContentStatus.DOWNLOADING;
      }
    },
    /**
     * Downloaded status.
     */
    DOWNLOADED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.ContentStatus toDTO() {
        return software.wings.beans.artifact.Artifact.ContentStatus.DOWNLOADED;
      }
    },
    /**
     * Waiting status.
     */
    DELETED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.ContentStatus toDTO() {
        return software.wings.beans.artifact.Artifact.ContentStatus.DELETED;
      }
    },

    /**
     * Failed status
     *
     */
    FAILED(true) {
      @Override
      public software.wings.beans.artifact.Artifact.ContentStatus toDTO() {
        return software.wings.beans.artifact.Artifact.ContentStatus.FAILED;
      }
    };

    ContentStatus() {}

    ContentStatus(boolean finalStatus) {
      this.finalStatus = finalStatus;
    }

    public boolean isFinalStatus() {
      return finalStatus;
    }

    private boolean finalStatus;

    public abstract software.wings.beans.artifact.Artifact.ContentStatus toDTO();
  }

  public static final class Builder {
    private transient String entityYamlPath; // TODO:: remove it with changeSet batching
    protected String appId;
    private String artifactStreamId;
    private String artifactSourceName;
    private ArtifactMetadata metadata = new ArtifactMetadata();
    private LateBindingMap label;
    private String displayName;
    private String revision;
    private List<String> serviceIds = new ArrayList<>();
    private String uuid;
    private List<ArtifactFile> artifactFiles = Lists.newArrayList();
    private List<ArtifactFileMetadata> artifactFileMetadata = Lists.newArrayList();
    private EmbeddedUser createdBy;
    private long createdAt;
    private Status status;
    private String description;
    private EmbeddedUser lastUpdatedBy;
    private String errorMessage;
    private long lastUpdatedAt;
    private ContentStatus contentStatus;
    private String settingId;
    private String accountId;
    private String artifactStreamType;
    private String uiDisplayName;

    private String buildNo;

    private Builder() {}

    public static Builder anArtifact() {
      return new Builder();
    }

    public Builder withArtifactStreamId(String artifactStreamId) {
      this.artifactStreamId = artifactStreamId;
      return this;
    }

    public Builder withArtifactSourceName(String artifactSourceName) {
      this.artifactSourceName = artifactSourceName;
      return this;
    }

    public Builder withMetadata(ArtifactMetadata metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder withDisplayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public Builder withRevision(String revision) {
      this.revision = revision;
      return this;
    }

    public Builder withServiceIds(List<String> serviceIds) {
      this.serviceIds = serviceIds;
      return this;
    }

    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    public Builder withArtifactFiles(List<ArtifactFile> artifactFiles) {
      this.artifactFiles = artifactFiles;
      return this;
    }

    public Builder withArtifactDownloadMetadata(List<ArtifactFileMetadata> artifactFileMetadata) {
      this.artifactFileMetadata = artifactFileMetadata;
      return this;
    }

    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public Builder withStatus(Status status) {
      this.status = status;
      return this;
    }

    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    public Builder withErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public Builder withLabel(LateBindingMap label) {
      this.label = label;
      return this;
    }

    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    public Builder withContentStatus(ContentStatus contentStatus) {
      this.contentStatus = contentStatus;
      return this;
    }

    public Builder withEntityYamlPath(String entityYamlPath) {
      this.entityYamlPath = entityYamlPath;
      return this;
    }

    public Builder withSettingId(String settingId) {
      this.settingId = settingId;
      return this;
    }

    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }

    public Builder withArtifactStreamType(String artifactStreamType) {
      this.artifactStreamType = artifactStreamType;
      return this;
    }

    public Builder withUiDisplayName(String uiDisplayName) {
      this.uiDisplayName = uiDisplayName;
      return this;
    }

    public Builder withBuildNo(String buildNo) {
      this.buildNo = buildNo;
      return this;
    }

    public Builder but() {
      return anArtifact()
          .withArtifactStreamId(artifactStreamId)
          .withArtifactSourceName(artifactSourceName)
          .withMetadata(metadata)
          .withLabel(label)
          .withDisplayName(displayName)
          .withRevision(revision)
          .withServiceIds(serviceIds)
          .withUuid(uuid)
          .withAppId(appId)
          .withArtifactFiles(artifactFiles)
          .withArtifactDownloadMetadata(artifactFileMetadata)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withStatus(status)
          .withDescription(description)
          .withLastUpdatedBy(lastUpdatedBy)
          .withErrorMessage(errorMessage)
          .withLastUpdatedAt(lastUpdatedAt)
          .withContentStatus(contentStatus)
          .withEntityYamlPath(entityYamlPath)
          .withSettingId(settingId)
          .withAccountId(accountId)
          .withArtifactStreamType(artifactStreamType)
          .withBuildNo(buildNo)
          .withUiDisplayName(uiDisplayName);
    }

    public Artifact build() {
      Artifact artifact = new Artifact();
      artifact.setArtifactStreamId(artifactStreamId);
      artifact.setArtifactSourceName(artifactSourceName);
      artifact.setMetadata(metadata);
      artifact.setLabel(label);
      artifact.setDisplayName(displayName);
      artifact.setRevision(revision);
      artifact.setServiceIds(serviceIds);
      artifact.setUuid(uuid);
      artifact.setAppId(appId);
      artifact.setArtifactFiles(artifactFiles);
      artifact.setArtifactFileMetadata(artifactFileMetadata);
      artifact.setCreatedBy(createdBy);
      artifact.setCreatedAt(createdAt);
      artifact.setStatus(status);
      artifact.setDescription(description);
      artifact.setLastUpdatedBy(lastUpdatedBy);
      artifact.setErrorMessage(errorMessage);
      artifact.setLastUpdatedAt(lastUpdatedAt);
      artifact.setContentStatus(contentStatus);
      artifact.setEntityYamlPath(entityYamlPath);
      artifact.setSettingId(settingId);
      artifact.setAccountId(accountId);
      artifact.setArtifactStreamType(artifactStreamType);
      artifact.setUiDisplayName(uiDisplayName);
      return artifact;
    }
  }

  public software.wings.beans.artifact.Artifact toDTO() {
    software.wings.beans.artifact.Artifact artifact = new software.wings.beans.artifact.Artifact();
    artifact.setArtifactStreamId(artifactStreamId);
    artifact.setArtifactSourceName(artifactSourceName);
    artifact.setMetadata(metadata);
    artifact.setLabel(label);
    artifact.setDisplayName(displayName);
    artifact.setRevision(revision);
    artifact.setServiceIds(serviceIds);
    artifact.setUuid(uuid);
    artifact.setAppId(appId);
    artifact.setArtifactFiles(artifactFiles.stream().map(ArtifactFile::toDTO).collect(Collectors.toList()));
    artifact.setArtifactFileMetadata(artifactFileMetadata);
    artifact.setCreatedBy(createdBy);
    artifact.setCreatedAt(createdAt);
    artifact.setStatus(status != null ? status.toDTO() : null);
    artifact.setDescription(description);
    artifact.setLastUpdatedBy(lastUpdatedBy);
    artifact.setErrorMessage(errorMessage);
    artifact.setLastUpdatedAt(lastUpdatedAt);
    artifact.setContentStatus(contentStatus != null ? contentStatus.toDTO() : null);
    artifact.setEntityYamlPath(entityYamlPath);
    artifact.setSettingId(settingId);
    artifact.setAccountId(accountId);
    artifact.setArtifactStreamType(artifactStreamType);
    artifact.setUiDisplayName(uiDisplayName);
    return artifact;
  }
}
