package io.harness.idp.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.morphia.annotations.Entity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.Wither;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "EnvironmentVariableKeys")
@StoreIn(DbAliases.IDP)
@Entity(value = "environmentVariable", noClassnameStored = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Document("environmentVariable")
@OwnedBy(HarnessTeam.IDP)
public class EnvironmentVariable implements PersistentEntity {
    private String envName;
    private String accountIdentifier;
    private String secretIdentifier;
    @Wither @CreatedDate Long createdAt;
    @Wither @LastModifiedDate Long lastModifiedAt;
}
