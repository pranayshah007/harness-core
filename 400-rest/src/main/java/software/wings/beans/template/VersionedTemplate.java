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
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import software.wings.beans.Base;
import software.wings.beans.Variable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;

@JsonInclude(NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "versionedTemplate", noClassnameStored = true)
@HarnessEntity(exportable = true)
@FieldNameConstants(innerTypeName = "VersionedTemplateKeys")
public class VersionedTemplate extends Base implements AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("yaml")
                 .unique(true)
                 .field(VersionedTemplateKeys.accountId)
                 .field(VersionedTemplateKeys.templateId)
                 .field(VersionedTemplateKeys.version)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("referencedTemplates")
                 .field("templateObject.referencedTemplateList.templateReference.templateUuid")
                 .build())
        .build();
  }

  public static final String TEMPLATE_ID_KEY = "templateId";
  public static final String VERSION_KEY = "version";

  private String templateId;
  private Long version;
  private String importedTemplateVersion;
  private String accountId;
  private String galleryId;
  @NotNull private BaseTemplate templateObject;
  private List<Variable> variables;
}
