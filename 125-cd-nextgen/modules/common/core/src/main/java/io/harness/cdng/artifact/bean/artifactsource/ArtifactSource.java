/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.artifactsource;
import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Artifacts Streams like Docker Hub, Nexus, GCR, etc.
 * This is the resolved artifact stream, can be used for Artifact Collection, etc.has
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants(innerTypeName = "ArtifactSourceKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "artifactSourceNG")
@Document("artifactSourceNG")
@TypeAlias("artifactSourceNG")
@HarnessEntity(exportable = true)
public abstract class ArtifactSource
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder().name("uniqueHash").unique(true).field(ArtifactSourceKeys.uniqueHash).build())
        .build();
  }
  @Id @dev.morphia.annotations.Id private String uuid;
  @NotNull private String accountId;
  /** It gives the artifact source type.*/
  @NotNull private ArtifactSourceType sourceType;
  /** This uniquely identifies one artifact stream based on its parameters.*/
  @NotNull private String uniqueHash;

  @SchemaIgnore @FdIndex @CreatedDate private long createdAt;
  @SchemaIgnore @NotNull @LastModifiedDate private long lastUpdatedAt;
  @Version Long version;
}
