package io.harness.ci.beans.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.harness.annotation.HarnessEntity;
import io.harness.annotation.RecasterAlias;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import static io.harness.annotations.dev.HarnessTeam.CI;

@Data
@Builder
@OwnedBy(CI)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "CITelemetrySentStatusKeys")
@Entity(value = "ciTelemetrySentStatus", noClassnameStored = true)
@StoreIn(DbAliases.CIMANAGER)
@Document("cITelemetrySentStatus")
@TypeAlias("cITelemetrySentStatus")
@RecasterAlias("io.harness.ci.beans.entities.CITelemetrySentStatus")
@HarnessEntity(exportable = true)
public class CITelemetrySentStatus {
    @Id
    @org.mongodb.morphia.annotations.Id String accountId;
    long lastSent;
}
