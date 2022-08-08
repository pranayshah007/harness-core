/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.notifications;

import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import io.harness.ccm.commons.entities.notifications.CCMNotificationSetting;
import io.harness.ccm.commons.entities.notifications.CCMNotificationSetting.CCMNotificationSettingKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.List;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

public class CCMNotificationsDao {
  @Inject private HPersistence persistence;

  public String save(CCMNotificationSetting notificationSetting) {
    return persistence.save(notificationSetting);
  }

  public CCMNotificationSetting get(String notificationSettingId) {
    return persistence.createQuery(CCMNotificationSetting.class)
        .field(CCMNotificationSettingKeys.uuid)
        .equal(notificationSettingId)
        .get();
  }

  public CCMNotificationSetting get(String perspectiveId, String accountId) {
    return persistence.createQuery(CCMNotificationSetting.class)
        .field(CCMNotificationSettingKeys.perspectiveId)
        .equal(perspectiveId)
        .field(CCMNotificationSettingKeys.accountId)
        .equal(accountId)
        .get();
  }

  public List<CCMNotificationSetting> list(String accountId) {
    return persistence.createQuery(CCMNotificationSetting.class)
        .field(CCMNotificationSettingKeys.accountId)
        .equal(accountId)
        .asList();
  }

  public CCMNotificationSetting upsert(CCMNotificationSetting notificationSetting) {
    Query query = persistence.createQuery(CCMNotificationSetting.class)
                      .field(CCMNotificationSettingKeys.perspectiveId)
                      .equal(notificationSetting.getPerspectiveId())
                      .field(CCMNotificationSettingKeys.accountId)
                      .equal(notificationSetting.getAccountId());

    UpdateOperations<CCMNotificationSetting> updateOperations =
        persistence.createUpdateOperations(CCMNotificationSetting.class)
            .set(CCMNotificationSettingKeys.channels, notificationSetting.getChannels());

    return persistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  public boolean delete(String perspectiveId, String accountId) {
    Query query = persistence.createQuery(CCMNotificationSetting.class)
                      .field(CCMNotificationSettingKeys.perspectiveId)
                      .equal(perspectiveId)
                      .field(CCMNotificationSettingKeys.accountId)
                      .equal(accountId);
    return persistence.delete(query);
  }
}
