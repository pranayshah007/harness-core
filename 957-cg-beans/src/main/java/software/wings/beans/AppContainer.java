/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.beans.EmbeddedUser;
import io.harness.delegate.beans.ChecksumType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;

import software.wings.utils.ContainerFamily;
import software.wings.utils.FileType;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.FieldNameConstants;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.mongodb.morphia.annotations.Entity;

/**
 * Application bean class.
 */

@Entity(value = "appContainers", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "AppContainerKeys")
public class AppContainer extends BaseFile {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("unique_yaml").unique(true).field("accountId").field("name").build())
        .build();
  }

  @FormDataParam("standard") private boolean standard;
  @FormDataParam("description") private String description;
  private boolean standardUpload;
  @FormDataParam("family") private ContainerFamily family;
  private String stackRootDirectory;
  private FileType fileType;
  private boolean systemCreated;
  @FormDataParam("version") private String version;
  private boolean hardened;

  /**
   * Is standard boolean.
   *
   * @return the boolean
   */
  public boolean isStandard() {
    return standard;
  }

  /**
   * Sets standard.
   *
   * @param standard the standard
   */
  public void setStandard(boolean standard) {
    this.standard = standard;
  }

  /**
   * Gets description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Is standard upload boolean.
   *
   * @return the boolean
   */
  public boolean isStandardUpload() {
    return standardUpload;
  }

  /**
   * Sets standard upload.
   *
   * @param standardUpload the standard upload
   */
  public void setStandardUpload(boolean standardUpload) {
    this.standardUpload = standardUpload;
  }

  /**
   * Getter for property 'family'.
   *
   * @return Value for property 'family'.
   */
  public ContainerFamily getFamily() {
    return family;
  }

  /**
   * Setter for property 'family'.
   *
   * @param family Value to set for property 'family'.
   */
  public void setFamily(ContainerFamily family) {
    this.family = family;
  }

  /**
   * Gets stack root directory.
   *
   * @return the stack root directory
   */
  public String getStackRootDirectory() {
    return stackRootDirectory;
  }

  /**
   * Sets stack root directory.
   *
   * @param stackRootDirectory the stack root directory
   */
  public void setStackRootDirectory(String stackRootDirectory) {
    this.stackRootDirectory = stackRootDirectory;
  }

  /**
   * Gets file type.
   *
   * @return the file type
   */
  public FileType getFileType() {
    return fileType;
  }

  /**
   * Sets file type.
   *
   * @param fileType the file type
   */
  public void setFileType(FileType fileType) {
    this.fileType = fileType;
  }

  public boolean isSystemCreated() {
    return systemCreated;
  }

  public void setSystemCreated(boolean systemCreated) {
    this.systemCreated = systemCreated;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
  public boolean isHardened() {
    return hardened;
  }

  public void setHardened(boolean hardened) {
    this.hardened = hardened;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode()
        + Objects.hash(standard, description, standardUpload, family, stackRootDirectory, fileType, accountId,
            systemCreated, hardened, version);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("standard", standard)
        .add("description", description)
        .add("standardUpload", standardUpload)
        .add("family", family)
        .add("stackRootDirectory", stackRootDirectory)
        .add("fileType", fileType)
        .add("accountId", accountId)
        .add("systemCreated", systemCreated)
        .toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    if (!super.equals(obj)) {
      return false;
    }
    final AppContainer other = (AppContainer) obj;
    return Objects.equals(this.standard, other.standard) && Objects.equals(this.description, other.description)
        && Objects.equals(this.standardUpload, other.standardUpload) && this.family == other.family
        && Objects.equals(this.stackRootDirectory, other.stackRootDirectory) && this.fileType == other.fileType
        && Objects.equals(this.accountId, other.accountId) && Objects.equals(this.systemCreated, other.systemCreated)
        && Objects.equals(this.version, other.version) && Objects.equals(this.hardened, other.hardened);
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String name;
    private String fileUuid;
    private String fileName;
    private String mimeType;
    private long size;
    private ChecksumType checksumType = ChecksumType.MD5;
    private String checksum;
    private boolean standard;
    private String description;
    private boolean standardUpload;
    private ContainerFamily family;
    private String stackRootDirectory;
    private FileType fileType;
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private boolean systemCreated;
    private String accountId;
    private String version;
    private boolean hardened;

    private Builder() {}

    /**
     * An app container builder.
     *
     * @return the builder
     */
    public static Builder anAppContainer() {
      return new Builder();
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With file uuid builder.
     *
     * @param fileUuid the file uuid
     * @return the builder
     */
    public Builder withFileUuid(String fileUuid) {
      this.fileUuid = fileUuid;
      return this;
    }

    /**
     * With file name builder.
     *
     * @param fileName the file name
     * @return the builder
     */
    public Builder withFileName(String fileName) {
      this.fileName = fileName;
      return this;
    }

    /**
     * With mime type builder.
     *
     * @param mimeType the mime type
     * @return the builder
     */
    public Builder withMimeType(String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    /**
     * With size builder.
     *
     * @param size the size
     * @return the builder
     */
    public Builder withSize(long size) {
      this.size = size;
      return this;
    }

    /**
     * With checksum type builder.
     *
     * @param checksumType the checksum type
     * @return the builder
     */
    public Builder withChecksumType(ChecksumType checksumType) {
      this.checksumType = checksumType;
      return this;
    }

    /**
     * With checksum builder.
     *
     * @param checksum the checksum
     * @return the builder
     */
    public Builder withChecksum(String checksum) {
      this.checksum = checksum;
      return this;
    }

    /**
     * With standard builder.
     *
     * @param standard the standard
     * @return the builder
     */
    public Builder withStandard(boolean standard) {
      this.standard = standard;
      return this;
    }

    /**
     * With description builder.
     *
     * @param description the description
     * @return the builder
     */
    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    /**
     * With standard upload builder.
     *
     * @param standardUpload the standard upload
     * @return the builder
     */
    public Builder withStandardUpload(boolean standardUpload) {
      this.standardUpload = standardUpload;
      return this;
    }

    /**
     * With family builder.
     *
     * @param family the family
     * @return the builder
     */
    public Builder withFamily(ContainerFamily family) {
      this.family = family;
      return this;
    }

    /**
     * With stack root directory builder.
     *
     * @param stackRootDirectory the stack root directory
     * @return the builder
     */
    public Builder withStackRootDirectory(String stackRootDirectory) {
      this.stackRootDirectory = stackRootDirectory;
      return this;
    }

    /**
     * With file type builder.
     *
     * @param fileType the file type
     * @return the builder
     */
    public Builder withFileType(FileType fileType) {
      this.fileType = fileType;
      return this;
    }

    /**
     * With uuid builder.
     *
     * @param uuid the uuid
     * @return the builder
     */
    public Builder withUuid(String uuid) {
      this.uuid = uuid;
      return this;
    }

    /**
     * With app id builder.
     *
     * @param appId the app id
     * @return the builder
     */
    public Builder withAppId(String appId) {
      this.appId = appId;
      return this;
    }

    /**
     * With created by builder.
     *
     * @param createdBy the created by
     * @return the builder
     */
    public Builder withCreatedBy(EmbeddedUser createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    /**
     * With created at builder.
     *
     * @param createdAt the created at
     * @return the builder
     */
    public Builder withCreatedAt(long createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * With last updated by builder.
     *
     * @param lastUpdatedBy the last updated by
     * @return the builder
     */
    public Builder withLastUpdatedBy(EmbeddedUser lastUpdatedBy) {
      this.lastUpdatedBy = lastUpdatedBy;
      return this;
    }

    /**
     * With last updated at builder.
     *
     * @param lastUpdatedAt the last updated at
     * @return the builder
     */
    public Builder withLastUpdatedAt(long lastUpdatedAt) {
      this.lastUpdatedAt = lastUpdatedAt;
      return this;
    }

    /**
     * With accountid at builder
     * @param accountId
     * @return
     */
    public Builder withAccountId(String accountId) {
      this.accountId = accountId;
      return this;
    }
    /**
     * With System Created at builder.
     * @param systemCreated
     * @return
     */
    public Builder withSystemCreated(boolean systemCreated) {
      this.systemCreated = systemCreated;
      return this;
    }

    /**
     * With Hardened at builder.
     */
    public Builder withHardened(boolean hardened) {
      this.hardened = hardened;
      return this;
    }

    /**
     * With Version at builder
     */
    public Builder withVersion(String version) {
      this.version = version;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return anAppContainer()
          .withName(name)
          .withFileUuid(fileUuid)
          .withFileName(fileName)
          .withMimeType(mimeType)
          .withSize(size)
          .withChecksumType(checksumType)
          .withChecksum(checksum)
          .withStandard(standard)
          .withDescription(description)
          .withStandardUpload(standardUpload)
          .withFamily(family)
          .withStackRootDirectory(stackRootDirectory)
          .withFileType(fileType)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withSystemCreated(systemCreated)
          .withAccountId(accountId)
          .withHardened(hardened)
          .withVersion(version);
    }

    /**
     * Build app container.
     *
     * @return the app container
     */
    public AppContainer build() {
      AppContainer appContainer = new AppContainer();
      appContainer.setName(name);
      appContainer.setFileUuid(fileUuid);
      appContainer.setFileName(fileName);
      appContainer.setMimeType(mimeType);
      appContainer.setSize(size);
      appContainer.setChecksumType(checksumType);
      appContainer.setChecksum(checksum);
      appContainer.setStandard(standard);
      appContainer.setDescription(description);
      appContainer.setStandardUpload(standardUpload);
      appContainer.setFamily(family);
      appContainer.setStackRootDirectory(stackRootDirectory);
      appContainer.setFileType(fileType);
      appContainer.setUuid(uuid);
      appContainer.setAppId(appId);
      appContainer.setCreatedBy(createdBy);
      appContainer.setCreatedAt(createdAt);
      appContainer.setLastUpdatedBy(lastUpdatedBy);
      appContainer.setLastUpdatedAt(lastUpdatedAt);
      appContainer.setSystemCreated(systemCreated);
      appContainer.setAccountId(accountId);
      appContainer.setHardened(hardened);
      appContainer.setVersion(version);
      return appContainer;
    }
  }
}
