/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;
import io.harness.validation.Update;

import software.wings.beans.marketplace.MarketPlaceType;

import com.github.reinert.jjschema.SchemaIgnore;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(PL)
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "marketPlaces", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class MarketPlace implements PersistentEntity, UuidAware, CreatedAtAware, CreatedByAware, UpdatedAtAware,
                                    UpdatedByAware, AccountAccess {
  private MarketPlaceType type;

  @FdUniqueIndex private String customerIdentificationCode;

  // harness account Id
  private String accountId;

  private String token;

  private Integer orderQuantity;
  private Date expirationDate;
  private String productCode;
  private String licenseType;
  private String dimension;

  @Id @NotNull(groups = {Update.class}) @SchemaIgnore private String uuid;

  @SchemaIgnore private EmbeddedUser createdBy;
  @SchemaIgnore @FdIndex private long createdAt;

  @SchemaIgnore private EmbeddedUser lastUpdatedBy;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
}
