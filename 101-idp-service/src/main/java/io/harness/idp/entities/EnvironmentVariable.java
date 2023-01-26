package io.harness.idp.entities;

import com.google.common.collect.ImmutableList;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Builder
@FieldNameConstants(innerTypeName = "EnvironmentVariableKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "environmentVariable", noClassnameStored = true)
@Document("environmentVariable")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class EnvironmentVariable implements PersistentEntity {
    public static List<MongoIndex> mongoIndexes() {
        return ImmutableList.<MongoIndex>builder()
                .add(CompoundMongoIndex.builder()
                        .name("unique_account_envName_secretId")
                        .unique(true)
                        .field(EnvironmentVariableKeys.accountIdentifier)
                        .field(EnvironmentVariableKeys.envName)
                        .field(EnvironmentVariableKeys.secretIdentifier)
                        .build())
                .build();
    }
    @Id @org.mongodb.morphia.annotations.Id private String id;
    private String envName;
    private String accountIdentifier;
    private String secretIdentifier;
    @CreatedDate Long createdAt;
    @LastModifiedDate Long lastModifiedAt;
    private boolean isDeleted;
    private long deletedAt;
}
