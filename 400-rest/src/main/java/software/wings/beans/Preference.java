/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "preferenceType")
@JsonSubTypes({
  @Type(value = DeploymentPreference.class, name = "DEPLOYMENT_PREFERENCE")
  , @Type(value = AuditPreference.class, name = "AUDIT_PREFERENCE")
})
@FieldNameConstants(innerTypeName = "PreferenceKeys")

@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "preferences")
@HarnessEntity(exportable = true)

public abstract class Preference extends Base implements AccountAccess {
  public Preference(String preferenceType) {
    this.preferenceType = preferenceType;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("preference_index")
                 .field(PreferenceKeys.accountId)
                 .field(PreferenceKeys.userId)
                 .field(PreferenceKeys.name)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("usergroupsId_shared")
                 .field(PreferenceKeys.accountId)
                 .field(PreferenceKeys.userId)
                 .field(PreferenceKeys.name)
                 .field(PreferenceKeys.userGroupsIdToShare)
                 .build())
        .build();
  }

  @NotEmpty private String name;
  @NotEmpty private String accountId;
  @NotEmpty private String userId;
  @NotEmpty private Set<String> userGroupsIdToShare;
  private String preferenceType;

  @UtilityClass
  public static final class PreferenceKeys {
    // Temporary
    public static final String createdAt = "createdAt";
    public static final String uuid = "uuid";
  }
}
