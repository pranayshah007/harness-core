/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.template;

import static java.util.Arrays.asList;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;

import software.wings.beans.Base;
import software.wings.beans.entityinterface.KeywordsAware;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity("templateGalleries")
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "TemplateGalleryKeys")
public class TemplateGallery extends Base implements KeywordsAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("account_gallery_key_idx")
                 .field(TemplateGalleryKeys.accountId)
                 .field(TemplateGalleryKeys.galleryKey)
                 .build())
        .build();
  }

  public static final String ACCOUNT_NAME_KEY = "accountName";
  public static final String NAME_KEY = "name";
  public static final String GALLERY_KEY = "galleryKey";

  public static final String IMPORTED_TEMPLATE_GALLERY_NAME = "Imported Templates";

  @NotEmpty private String name;
  @NotEmpty private String accountId;
  private String description;
  private String referencedGalleryId;
  private boolean global;
  @SchemaIgnore private Set<String> keywords;
  private String galleryKey;

  public enum GalleryKey { HARNESS_COMMAND_LIBRARY_GALLERY, ACCOUNT_TEMPLATE_GALLERY }

  @Builder
  public TemplateGallery(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, Set<String> keywords, String entityYamlPath, String name, String accountId,
      String description, String referencedGalleryId, boolean global, String galleryKey) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.name = name;
    this.accountId = accountId;
    this.description = description;
    this.referencedGalleryId = referencedGalleryId;
    this.global = global;
    this.keywords = keywords;
    this.galleryKey = galleryKey;
  }

  @Override
  public Set<String> generateKeywords() {
    Set<String> keywords = KeywordsAware.super.generateKeywords();
    keywords.addAll(asList(name, description));
    return keywords;
  }
}
