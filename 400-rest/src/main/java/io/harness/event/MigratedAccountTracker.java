package io.harness.event;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@OwnedBy(CDC)
@Data
@Builder
@FieldNameConstants(innerTypeName = "MigratedAccountTrackerKeys")
@StoreIn(DbAliases.HARNESS)
@Entity(value = "MigratedAccountTracker", noClassnameStored = true)
public class MigratedAccountTracker implements PersistentEntity {
  @Id private String migrationNumber;
  @Getter @Setter private List<String> accountIds;
  @Getter @Setter private String migrationClassName;
}
