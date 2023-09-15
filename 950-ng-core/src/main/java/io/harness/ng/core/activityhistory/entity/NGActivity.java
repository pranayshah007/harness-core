/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.activityhistory.entity;

import io.harness.annotations.StoreIn;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccountAccess;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "ActivityHistoryEntityKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@StoreIn(DbAliases.NG_MANAGER)
@Document("entityActivity")
@Entity(value = "entityActivity", noClassnameStored = true)
@Persistent
public class NGActivity implements PersistentEntity, NGAccountAccess {
  @Id @dev.morphia.annotations.Id String id;
  @FdIndex @NotBlank String accountIdentifier;
  @NotNull EntityDetail referredEntity;
  String referredEntityFQN;
  @NotNull String referredEntityType;
  @NotNull String type;
  String activityStatus;
  @NotNull long activityTime;
  String description;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  @UtilityClass
  public static final class ActivityHistoryEntityKeys {
    public static final String referredByEntityType = "referredByEntityType";
    public static final String usageType = "usageDetail.usageType";

    public static final String referredByEntityName = "referredByEntity.name";
    public static final String referredByEntityIdentifier = "referredByEntity.entityRef.identifier";
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("referredFqn_type_time_referredByType")
                 .field(ActivityHistoryEntityKeys.referredEntityFQN)
                 .field(ActivityHistoryEntityKeys.referredEntityType)
                 .field(ActivityHistoryEntityKeys.activityTime)
                 .field(ActivityHistoryEntityKeys.referredByEntityType)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("referredFqn_type_referredByType")
                 .field(ActivityHistoryEntityKeys.referredEntityFQN)
                 .field(ActivityHistoryEntityKeys.referredEntityType)
                 .field(ActivityHistoryEntityKeys.referredByEntityType)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("type_referredFqn_referredByType_desSort_Index")
                 .field(ActivityHistoryEntityKeys.type)
                 .field(ActivityHistoryEntityKeys.referredEntityFQN)
                 .field(ActivityHistoryEntityKeys.referredEntityType)
                 .descSortField(ActivityHistoryEntityKeys.activityTime)
                 .field(ActivityHistoryEntityKeys.referredByEntityType)
                 .build())
        .build();
  }
}
