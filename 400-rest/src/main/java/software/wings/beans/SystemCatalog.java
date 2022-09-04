/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.delegate.beans.ChecksumType;
import io.harness.ng.DbAliases;

import software.wings.utils.ContainerFamily;
import software.wings.utils.FileType;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by sgurubelli on 5/23/17.
 */
@StoreIn(DbAliases.HARNESS)
@Entity(value = "systemCatalogs", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class SystemCatalog extends BaseFile {
  private CatalogType catalogType;
  private String stackRootDirectory;
  private FileType fileType;
  private ContainerFamily family;
  private String notes;
  private String version;
  private boolean hardened;

  /**
   *  Getter for Catalog Type
   * @return
   */
  public CatalogType getCatalogType() {
    return catalogType;
  }

  /**
   *
   * @param catalogType
   */
  public void setCatalogType(CatalogType catalogType) {
    this.catalogType = catalogType;
  }

  /**
   *
   * @return
   */
  public String getStackRootDirectory() {
    return stackRootDirectory;
  }

  /**
   *
   * @param stackRootDirectory
   */
  public void setStackRootDirectory(String stackRootDirectory) {
    this.stackRootDirectory = stackRootDirectory;
  }

  /**
   *
   * @return
   */
  public FileType getFileType() {
    return fileType;
  }

  /**
   * Setter for property 'fileType'
   *
   * @param fileType
   */
  public void setFileType(FileType fileType) {
    this.fileType = fileType;
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
   * Get notes
   * @return
   */
  public String getNotes() {
    return notes;
  }

  /**
   * Set notes
   * @param notes
   */
  public void setNotes(String notes) {
    this.notes = notes;
  }

  /**
   * Get Version
   * @return
   */
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  /**
   *
   * @return
   */
  public boolean isHardened() {
    return hardened;
  }

  public void setHardened(boolean hardened) {
    this.hardened = hardened;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    SystemCatalog that = (SystemCatalog) o;
    return hardened == that.hardened && catalogType == that.catalogType
        && Objects.equals(stackRootDirectory, that.stackRootDirectory) && fileType == that.fileType
        && family == that.family && Objects.equals(notes, that.notes) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), catalogType, stackRootDirectory, fileType, family, notes, version, hardened);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("catalogType", catalogType)
        .add("stackRootDirectory", stackRootDirectory)
        .add("fileType", fileType)
        .add("family", family)
        .add("notes", notes)
        .add("version", version)
        .add("hardened", hardened)
        .toString();
  }

  /**
   * The enum Config override type.
   */
  public enum CatalogType {

    /**
     * Application Stacks
     */
    APPSTACK
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
    private String uuid;
    private String appId;
    private EmbeddedUser createdBy;
    private long createdAt;
    private EmbeddedUser lastUpdatedBy;
    private long lastUpdatedAt;
    private CatalogType catalogType;
    private String stackRootDirectory;
    private FileType fileType;
    private ContainerFamily family;
    private String notes;
    private String version;
    private boolean hardened;

    private Builder() {}

    /**
     * An app container builder.
     *
     * @return the builder
     */
    public static Builder aSystemCatalog() {
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
     * With catalogtype at builder
     * @param catalogType
     * @return
     */
    public Builder withCatalogType(CatalogType catalogType) {
      this.catalogType = catalogType;
      return this;
    }

    /**
     * With filetype at builder
     * @param fileType
     * @return
     */
    public Builder withFileType(FileType fileType) {
      this.fileType = fileType;
      return this;
    }

    /**
     * With stack root directory builder
     * @param stackRootDirectory
     * @return
     */
    public Builder withStackRootDirectory(String stackRootDirectory) {
      this.stackRootDirectory = stackRootDirectory;
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

    public Builder withNotes(String notes) {
      this.notes = notes;
      return this;
    }

    /**
     *
     * @param hardened
     * @return
     */
    public Builder withHardened(boolean hardened) {
      this.hardened = hardened;
      return this;
    }

    /**
     *
     * @param version
     * @return
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
      return aSystemCatalog()
          .withName(name)
          .withFileUuid(fileUuid)
          .withFileName(fileName)
          .withMimeType(mimeType)
          .withSize(size)
          .withChecksumType(checksumType)
          .withChecksum(checksum)
          .withUuid(uuid)
          .withAppId(appId)
          .withCreatedBy(createdBy)
          .withCreatedAt(createdAt)
          .withLastUpdatedBy(lastUpdatedBy)
          .withLastUpdatedAt(lastUpdatedAt)
          .withCatalogType(catalogType)
          .withFileType(fileType)
          .withStackRootDirectory(stackRootDirectory)
          .withFamily(family)
          .withNotes(notes)
          .withHardened(hardened)
          .withVersion(version);
    }

    /**
     * Build app container.
     *
     * @return the app container
     */
    public SystemCatalog build() {
      SystemCatalog systemCatalog = new SystemCatalog();
      systemCatalog.setName(name);
      systemCatalog.setFileUuid(fileUuid);
      systemCatalog.setFileName(fileName);
      systemCatalog.setMimeType(mimeType);
      systemCatalog.setSize(size);
      systemCatalog.setChecksumType(checksumType);
      systemCatalog.setChecksum(checksum);
      systemCatalog.setUuid(uuid);
      systemCatalog.setAppId(appId);
      systemCatalog.setCreatedBy(createdBy);
      systemCatalog.setCreatedAt(createdAt);
      systemCatalog.setLastUpdatedBy(lastUpdatedBy);
      systemCatalog.setLastUpdatedAt(lastUpdatedAt);
      systemCatalog.setCatalogType(catalogType);
      systemCatalog.setFileType(fileType);
      systemCatalog.setStackRootDirectory(stackRootDirectory);
      systemCatalog.setFamily(family);
      systemCatalog.setNotes(notes);
      systemCatalog.setVersion(version);
      return systemCatalog;
    }
  }
}
