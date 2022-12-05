package io.harness.delegate.beans;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Date;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "DelegateVersionKeys")
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateVersion", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class DelegateVersion implements PersistentEntity {
  @Id @NotEmpty private String delegateImage;
  @FdTtlIndex private final Date validUntil;
}
