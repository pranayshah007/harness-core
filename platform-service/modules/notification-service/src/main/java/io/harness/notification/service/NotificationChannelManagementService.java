package io.harness.notification.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationChannel.NotificationChannelKeys;
import io.harness.notification.repositories.NotificationChannelRepository;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class NotificationChannelManagementService
    implements io.harness.notification.service.api.NotificationChannelManagementService {
  private final NotificationChannelRepository notificationChannelRepository;

  @Override
  public NotificationChannel create(NotificationChannel notificationChannel) {
    NotificationChannel channel = notificationChannelRepository.save(notificationChannel);
    return channel;
  }

  @Override
  public NotificationChannel update(NotificationChannel notificationChannel) {
    return notificationChannelRepository.save(notificationChannel);
  }

  @Override
  public NotificationChannel getNotificationChannel(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria =
        createNotificationChannelFetchCriteria(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return notificationChannelRepository.findOne(criteria);
  }

  @Override
  public List<NotificationChannel> getNotificationChannelList(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return null;
  }

  @Override
  public boolean delete(NotificationChannel notificationChannel) {
    notificationChannelRepository.delete(notificationChannel);
    return true;
  }

  private Criteria createNotificationChannelFetchCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Criteria criteria = createScopeCriteria(accountIdentifier, orgIdentifier, projectIdentifier);
    criteria.and(NotificationChannelKeys.name).is(identifier);
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
