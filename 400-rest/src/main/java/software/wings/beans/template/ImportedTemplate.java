/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.template;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.entityinterface.ApplicationAccess;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@JsonInclude(NON_NULL)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ImportedTemplateKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "importedTemplates", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class ImportedTemplate implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                         UpdatedByAware, ApplicationAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_app_command_idx")
                 .field(ImportedTemplateKeys.accountId)
                 .field(ImportedTemplateKeys.appId)
                 .field(ImportedTemplateKeys.commandStoreName)
                 .field(ImportedTemplateKeys.commandName)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("template_idx")
                 .unique(true)
                 .field(ImportedTemplateKeys.templateId)
                 .build())
        .build();
  }

  @Id @NotNull(groups = {Update.class}) private String uuid;

  @FdIndex @NotNull protected String appId;

  private EmbeddedUser createdBy;

  @FdIndex private long createdAt;

  private EmbeddedUser lastUpdatedBy;

  @NotNull private long lastUpdatedAt;

  @NotEmpty private String name;
  @NotEmpty private String commandStoreName;
  @NotEmpty private String commandName;
  @NotEmpty private String templateId;
  private String description;
  private String imageUrl;
  private String repoUrl;
  private Set<String> tags;
  @NotEmpty private String accountId;

  @Builder(toBuilder = true)
  public ImportedTemplate(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String name, String commandStoreName, String commandName, String templateId,
      String description, String imageUrl, String accountId, String repoUrl, Set<String> tags) {
    this.uuid = uuid;
    this.appId = appId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
    this.lastUpdatedBy = lastUpdatedBy;
    this.lastUpdatedAt = lastUpdatedAt;
    this.name = name;
    this.commandStoreName = commandStoreName;
    this.commandName = commandName;
    this.templateId = templateId;
    this.description = description;
    this.imageUrl = imageUrl;
    this.accountId = accountId;
    this.repoUrl = repoUrl;
    this.tags = tags;
  }
}
