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
}
