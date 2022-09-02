/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.template;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.data.validator.EntityName;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.NameAccess;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.Base;
import software.wings.beans.entityinterface.KeywordsAware;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@NoArgsConstructor
@JsonInclude(NON_NULL)
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "TemplateFolderKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "templateFolders", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class TemplateFolder extends Base implements KeywordsAware, NameAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .unique(true)
                 .name("duplicateKey")
                 .field(TemplateFolderKeys.accountId)
                 .field(TemplateFolderKeys.name)
                 .field(TemplateFolderKeys.pathId)
                 .field(TemplateFolderKeys.appId)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("account_gallery_app_idx")
                 .field(TemplateFolderKeys.accountId)
                 .field(TemplateFolderKeys.galleryId)
                 .field(TemplateFolderKeys.appId)
                 .build())
        .build();
  }

  public static final String GALLERY_ID_KEY = "galleryId";
  public static final String KEYWORDS_KEY = "keywords";
  public static final String NAME_KEY = "name";
  public static final String PARENT_ID_KEY = "parentId";
  public static final String PATH_ID_KEY = "pathId";
  public static final String PATH_KEY = "path";

  @NotEmpty private String accountId;
  @NotEmpty @EntityName(groups = {Create.class, Update.class}) String name;
  private String description;
  private String parentId;
  private transient String nodeType = NodeType.FILE.name();
  private String galleryId;
  private transient long templatesCount;
  private String pathId;
  private transient List<TemplateFolder> children = new ArrayList<>();

  @SchemaIgnore private Set<String> keywords;

  public enum NodeType {
    FOLDER("folder"),
    FILE("file");

    private String displayName;

    NodeType(String displayName) {
      this.displayName = displayName;
    }
    @JsonValue
    public String getDisplayName() {
      return displayName;
    }
  }

  @Builder
  public TemplateFolder(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, Set<String> keywords, String entityYamlPath, String accountId, String name,
      String description, String parentId, String nodeType, String galleryId, String pathId,
      List<TemplateFolder> children) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.accountId = accountId;
    this.name = name;
    this.description = description;
    this.parentId = parentId;
    this.nodeType = nodeType;
    this.galleryId = galleryId;
    this.pathId = pathId;
    this.children = children;
    this.keywords = keywords;
  }

  public TemplateFolder cloneInternal() {
    return TemplateFolder.builder().name(name).description(description).appId(GLOBAL_APP_ID).build();
  }

  public void addChild(TemplateFolder child) {
    if (isEmpty(children)) {
      children = new ArrayList<>();
    }
    children.add(child);
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description));
    return keywords;
  }

  @UtilityClass
  public static final class TemplateFolderKeys {
    // Temporary
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
    public static final String name = "name";
    public static final String appId = "appId";
    public static final String accountId = "accountId";
  }
}
