/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HPersistence;

import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class SettingAttributeDao {
  @Inject private HPersistence persistence;

  public String save(SettingAttribute settingAttribute) {
    return persistence.save(settingAttribute);
  }

  public List<SettingAttribute> list(String accountId, SettingCategory category) {
    Query<SettingAttribute> query = persistence.createQuery(SettingAttribute.class)
                                        .field(SettingAttributeKeys.accountId)
                                        .equal(accountId)
                                        .field(SettingAttributeKeys.category)
                                        .equal(category);
    return query.asList();
  }
}
