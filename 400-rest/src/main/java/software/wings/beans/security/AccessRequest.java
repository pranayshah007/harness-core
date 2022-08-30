/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.security;

import static io.harness.annotations.dev.HarnessModule._970_RBAC_CORE;

import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.github.reinert.jjschema.SchemaIgnore;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.HARNESS)
@Entity(value = "accessRequest")
@FieldNameConstants(innerTypeName = "AccessRequestKeys")
@OwnedBy(HarnessTeam.PL)
@TargetModule(_970_RBAC_CORE)
public class AccessRequest
    implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess, PersistentRegularIterable {
  @Id @NotNull @SchemaIgnore private String uuid;
  @SchemaIgnore private long createdAt;
  @SchemaIgnore @NotNull private long lastUpdatedAt;
  @NotNull String accountId;
  @NotNull AccessType accessType;
  String harnessUserGroupId;
  private Set<String> memberIds;
  private long accessStartAt;
  private long accessEndAt;
  @DefaultValue("true") private boolean accessActive;
  private Long nextIteration;

  public enum AccessType { GROUP_ACCESS, MEMBER_ACCESS }
  @Override
  public Long obtainNextIteration(String fieldName) {
    return nextIteration;
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    this.nextIteration = nextIteration;
  }
}
