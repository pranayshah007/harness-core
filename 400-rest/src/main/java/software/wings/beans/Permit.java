/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.FdUniqueIndex;
import io.harness.ng.DbAliases;

import java.util.Date;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.mongodb.morphia.annotations.Entity;

/**
 * Created by anubhaw on 7/17/18.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.HARNESS)
@Entity(value = "permits", noClassnameStored = true)
@HarnessEntity(exportable = false)
@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
public class Permit extends Base {
  public static final String PERMIT_KEY_ID = "key";
  @FdUniqueIndex private String key;
  private String group;
  @FdTtlIndex private Date expireAt;
  private long leaseDuration;
  @FdIndex private String accountId;

  @Builder
  public Permit(String uuid, String appId, EmbeddedUser createdBy, long createdAt, EmbeddedUser lastUpdatedBy,
      long lastUpdatedAt, String entityYamlPath, String key, String group, Date expireAt, long leaseDuration,
      String accountId) {
    super(uuid, appId, createdBy, createdAt, lastUpdatedBy, lastUpdatedAt, entityYamlPath);
    this.key = key;
    this.group = group;
    this.expireAt = new Date(expireAt.getTime());
    this.leaseDuration = leaseDuration;
    this.accountId = accountId;
  }
}
