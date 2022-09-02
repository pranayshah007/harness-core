/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.peronalization;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;

import software.wings.beans.peronalization.PersonalizationSteps.PersonalizationStepsKeys;
import software.wings.beans.peronalization.PersonalizationTemplates.PersonalizationTemplatesKeys;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Value
@Builder
@FieldNameConstants(innerTypeName = "PersonalizationKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "personalization", noClassnameStored = true)
@HarnessEntity(exportable = true)
public class Personalization implements PersistentEntity, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_identification")
                 .unique(true)
                 .field(PersonalizationKeys.accountId)
                 .field(PersonalizationKeys.userId)
                 .build())
        .build();
  }
  @Id private ObjectId id;

  private String accountId;
  private String userId;

  private PersonalizationSteps steps;

  private PersonalizationTemplates templates;

  @UtilityClass
  public static final class PersonalizationKeys {
    public static final String steps_favorites = steps + "." + PersonalizationStepsKeys.favorites;
    public static final String steps_recent = steps + "." + PersonalizationStepsKeys.recent;
    public static final String templates_favorites = templates + "." + PersonalizationTemplatesKeys.favorites;
  }
}
