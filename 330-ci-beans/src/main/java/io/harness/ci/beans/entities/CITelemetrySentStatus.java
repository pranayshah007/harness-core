package io.harness.ci.beans.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexed;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CI;

@Data
@Builder
@OwnedBy(CI)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CITelemetrySentStatusKeys")
@Entity(value = "ciTelemetrySentStatus", noClassnameStored = true)
@StoreIn(DbAliases.CIMANAGER)
@Document("ciTelemetrySentStatus")
@TypeAlias("ciTelemetrySentStatus")
@RecasterAlias("io.harness.ci.beans.entities.CITelemetrySentStatus")
@HarnessEntity(exportable = true)
public class CITelemetrySentStatus implements UuidAware, PersistentEntity {
    @Id
    @org.mongodb.morphia.annotations.Id String uuid;
    public static List<MongoIndex> mongoIndexes() {
        return ImmutableList.<MongoIndex>builder()
                .add(CompoundMongoIndex.builder()
                        .name("no_dup")
                        .unique(true)
                        .field(CITelemetrySentStatus.CITelemetrySentStatusKeys.accountId)
                        .build())
                .build();
    }
    @Indexed(options = @IndexOptions(unique = true))
    String accountId;
    long lastSent; // timestamp
}
