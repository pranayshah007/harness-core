/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.alert;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;

import lombok.Value;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Value
@StoreIn(DbAliases.HARNESS)
@Entity(value = "notificationRulesStatuses", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class NotificationRulesStatus implements PersistentEntity, AccountAccess {
  @Id private String accountId;
  private boolean enabled;
}
