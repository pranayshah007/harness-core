package io.harness.entities;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "AppConfigKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "appConfig", noClassnameStored = true)
@Document("appConfig")
@Persistent
@OwnedBy(HarnessTeam.IDP)
public class AppConfig implements PersistentEntity {
    @Id @org.mongodb.morphia.annotations.Id private String id;
    private String accountIdentifier;
    @CreatedDate Long createdAt;
    @LastModifiedDate Long lastModifiedAt;
    private boolean isDeleted;
    private long deletedAt;
}
