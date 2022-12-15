/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.selection.log;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.SchemaIgnore;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "delegateSelectionLogTaskMetadata", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "DelegateSelectionLogTaskMetadataKeys")
public class DelegateSelectionLogTaskMetadata implements PersistentEntity, UuidAware, AccountAccess {
  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @FdIndex @NotEmpty private String accountId;
  @FdUniqueIndex @NotEmpty private String taskId;
  private Map<String, String> setupAbstractions;

  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(1).toInstant());
}
