/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.template;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.NameAccess;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Base;
import software.wings.beans.Variable;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.template.dto.ImportedTemplateDetails;
import software.wings.ngmigration.NGMigrationEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@JsonInclude(NON_NULL)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "TemplateKeys")
@Entity(value = "templates", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class Template extends Base implements KeywordsAware, NameAccess, NGMigrationEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(TemplateKeys.accountId)
                 .field(TemplateKeys.name)
                 .field(TemplateKeys.folderId)
                 .field(TemplateKeys.appId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_gallery_app_idx")
                 .field(TemplateKeys.accountId)
                 .field(TemplateKeys.galleryId)
                 .field(TemplateKeys.appId)
                 .build())
        .build();
  }

  public static final String FOLDER_ID_KEY = "folderId";
  public static final String FOLDER_PATH_ID_KEY = "folderPathId";
  public static final String GALLERY_ID_KEY = "galleryId";
  public static final String GALLERY_KEY = "gallery";
  public static final String KEYWORDS_KEY = "keywords";
  public static final String NAME_KEY = "name";
  public static final String TYPE_KEY = "type";
  public static final String VERSION_KEY = "version";
  public static final String REFERENCED_TEMPLATE_ID_KEY = "referencedTemplateId";
  public static final String APP_ID_KEY = "appId";

  @FdIndex @NotNull @EntityName(groups = {Create.class, Update.class}) private String name;
  @NotEmpty private String accountId;
  private String type;
  @FdIndex private String folderId;
  Long version;
  private transient String versionDetails;
  private String description;
  private String folderPathId;
  private transient String folderPath;
  private transient String gallery;
  @NotNull private transient BaseTemplate templateObject;
  private transient List<Variable> variables;
  private transient VersionedTemplate versionedTemplate;
  private String galleryId;
  private String referencedTemplateId;
  private Long referencedTemplateVersion;
  private transient ImportedTemplateDetails importedTemplateDetails;
  private TemplateMetadata templateMetadata;
  private transient String referencedTemplateUri;
  @SchemaIgnore private Set<String> keywords;

  @Builder
  public Template(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, Set<String> keywords, String entityYamlPath, String name, String accountId, String type,
      String folderId, long version, String description, String folderPathId, String folderPath, String gallery,
      BaseTemplate templateObject, List<Variable> variables, VersionedTemplate versionedTemplate, String galleryId,
      String referencedTemplateId, Long referencedTemplateVersion, String versionDetails,
      ImportedTemplateDetails importedTemplateDetails, TemplateMetadata templateMetadata) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.name = name;
    this.accountId = accountId;
    this.type = type;
    this.folderId = folderId;
    this.version = version;
    this.description = description;
    this.folderPathId = folderPathId;
    this.folderPath = folderPath;
    this.gallery = gallery;
    this.templateObject = templateObject;
    this.variables = variables;
    this.versionedTemplate = versionedTemplate;
    this.galleryId = galleryId;
    this.referencedTemplateId = referencedTemplateId;
    this.referencedTemplateVersion = referencedTemplateVersion;
    this.keywords = keywords;
    this.versionDetails = versionDetails;
    this.importedTemplateDetails = importedTemplateDetails;
    this.templateMetadata = templateMetadata;
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description, type));
    return keywords;
  }

  @UtilityClass
  public static final class TemplateKeys {
    // Temporary
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String name = "name";
    public static final String appId = "appId";
    public static final String accountId = "accountId";
  }

  @JsonIgnore
  @Override
  public String getMigrationEntityName() {
    return getName();
  }
}
