/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.DuplicateFieldException;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationChannel.NotificationChannelKeys;
import io.harness.notification.repositories.NotificationChannelRepository;

import com.google.inject.Inject;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NotificationChannelManagementServiceImpl
    implements io.harness.notification.service.api.NotificationChannelManagementService {
  private final NotificationChannelRepository notificationChannelRepository;

  @Override
  public NotificationChannel create(NotificationChannel notificationChannel) {
    try {
      //@TODO Validation check
      return notificationChannelRepository.save(notificationChannel);
    } catch (DuplicateKeyException duplicateKeyException) {
      throw new DuplicateFieldException(
          "Duplicate identifier, please try again with a new identifier", USER, duplicateKeyException);
    }
  }

  @Override
  public NotificationChannel update(NotificationChannel notificationChannel) {
    try {
      //@TODO Validation check
      return notificationChannelRepository.save(notificationChannel);
    } catch (DuplicateKeyException duplicateKeyException) {
      throw new DuplicateFieldException(
          "Duplicate identifier, please try again with a new identifier", USER, duplicateKeyException);
    }
  }

  @Override
  public NotificationChannel get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, @NotNull String identifier) {
    Criteria criteria =
        createNotificationChannelFetchCriteria(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return notificationChannelRepository.findOne(criteria);
  }

  @Override
  public List<NotificationChannel> getNotificationChannelList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return notificationChannelRepository.findAll(
        createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier));
  }

  @Override
  public boolean delete(NotificationChannel notificationChannel) {
    notificationChannelRepository.delete(notificationChannel);
    return true;
  }

  private Criteria createNotificationChannelFetchCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    criteria.and(NotificationChannelKeys.identifier).is(identifier);
    return criteria;
  }

  private Criteria createScopeCriteria(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();
    criteria.and(NotificationChannelKeys.accountIdentifier).is(accountIdentifier);
    criteria.and(NotificationChannelKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(NotificationChannelKeys.projectIdentifier).is(projectIdentifier);
    return criteria;
  }
}
