package io.harness.ci.beans.entities;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@OwnedBy(CI)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "CITelemetrySentStatusKeys")
@Entity(value = "ciTelemetrySentStatus", noClassnameStored = true)
@StoreIn(DbAliases.CIMANAGER)
@Document("ciTelemetrySentStatus")
@TypeAlias("ciTelemetrySentStatus")
@RecasterAlias("io.harness.ci.beans.entities.CITelemetrySentStatus")
@HarnessEntity(exportable = false)
public class CITelemetrySentStatus implements UuidAware, PersistentEntity {
  @Id @org.mongodb.morphia.annotations.Id String uuid;
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(
            CompoundMongoIndex.builder().name("no_dup").unique(true).field(CITelemetrySentStatusKeys.accountId).build())
        .build();
  }
  String accountId;
  long lastSent; // timestamp
}
