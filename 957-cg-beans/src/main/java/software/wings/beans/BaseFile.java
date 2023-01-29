/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.delegate.beans.ChecksumType;
import io.harness.validation.Create;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.MoreObjects;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 4/13/16.
 */
@OwnedBy(PL)
@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "BaseFileKeys")
@EqualsAndHashCode(callSuper = false)
public class BaseFile {
  @FormDataParam("name") private String name;
  private String fileUuid;
  @NotEmpty(groups = Create.class) private String fileName;
  private String mimeType;
  private long size;
  private ChecksumType checksumType = ChecksumType.MD5;
  @FormDataParam("md5") private String checksum;
  @NotEmpty protected String accountId;

  @EqualsAndHashCode.Exclude @Deprecated public static final String ID_KEY2 = "_id";
  @EqualsAndHashCode.Exclude @Deprecated public static final String APP_ID_KEY2 = "appId";
  @EqualsAndHashCode.Exclude @Deprecated public static final String ACCOUNT_ID_KEY2 = "accountId";
  @EqualsAndHashCode.Exclude @Deprecated public static final String LAST_UPDATED_AT_KEY2 = "lastUpdatedAt";

  @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;
  @NotNull @SchemaIgnore protected String appId;
  @EqualsAndHashCode.Exclude @SchemaIgnore private EmbeddedUser createdBy;
  @EqualsAndHashCode.Exclude @SchemaIgnore private long createdAt;

  @EqualsAndHashCode.Exclude @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @EqualsAndHashCode.Exclude @SchemaIgnore @NotNull private long lastUpdatedAt;

  /**
   * TODO: Add isDeleted boolean field to enable soft delete. @swagat
   */

  @JsonIgnore @SchemaIgnore private transient String entityYamlPath; // TODO:: remove it with changeSet batching

  @JsonIgnore
  @SchemaIgnore
  public String getEntityYamlPath() {
    return entityYamlPath;
  }

  @Setter @JsonIgnore @SchemaIgnore private transient boolean syncFromGit;

  @JsonIgnore
  @SchemaIgnore
  public boolean isSyncFromGit() {
    return syncFromGit;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fileUuid", fileUuid)
        .add("name", name)
        .add("fileName", fileName)
        .add("mimeType", mimeType)
        .add("size", size)
        .add("checksumType", checksumType)
        .add("checksum", checksum)
        .add("accountId", accountId)
        .add("uuid", uuid)
        .add("appId", appId)
        .toString();
  }
}
