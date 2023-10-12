/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.entity;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DirectHttpDataSourceLocationEntity.class, name = "DirectHttp")
  , @JsonSubTypes.Type(value = CustomHttpDataSourceLocationEntity.class, name = "CustomHttp"),
      @JsonSubTypes.Type(value = NoopDataSourceLocationEntity.class, name = "Noop")
})
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "DataSourceLocationKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "dataSourceLocations", noClassnameStored = true)
@Document("dataSourceLocations")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public abstract class DataSourceLocationEntity
    implements PersistentEntity, CreatedByAware, UpdatedByAware, CreatedAtAware, UpdatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_account_identifier")
                 .unique(true)
                 .field(DataSourceLocationKeys.accountIdentifier)
                 .field(DataSourceLocationKeys.identifier)
                 .build())
        .build();
  }

  @Id private String id;
  private String accountIdentifier;
  private String identifier;
  private String dataSourceIdentifier;
  @NotNull DataSourceLocationType type;
  @SchemaIgnore @CreatedBy private EmbeddedUser createdBy;
  @SchemaIgnore @LastModifiedBy private EmbeddedUser lastUpdatedBy;
  @Builder.Default @CreatedDate private long createdAt = System.currentTimeMillis();
  @LastModifiedDate private long lastUpdatedAt;
}
