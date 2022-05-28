/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.services.api.UpdatableEntity;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelType;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelUtils;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.MSTeamChannel;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.PagerDutyChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "NotificationRuleKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Entity(value = "notificationRules")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
@StoreIn(DbAliases.CVNG)
public abstract class NotificationRule
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(NotificationRuleKeys.accountId)
                 .field(NotificationRuleKeys.orgIdentifier)
                 .field(NotificationRuleKeys.projectIdentifier)
                 .field(NotificationRuleKeys.identifier)
                 .build())
        .build();
  }

  String accountId;
  String projectIdentifier;
  String orgIdentifier;
  @Id private String uuid;
  String identifier;
  String name;

  private long lastUpdatedAt;
  private long createdAt;
  private int version;

  NotificationRuleType type;
  CVNGNotificationChannel notificationMethod;

  @Data
  @SuperBuilder
  public abstract static class CVNGNotificationChannel {
    public abstract CVNGNotificationChannelType getType();
    public abstract NotificationChannel toNotificationChannel(String accountId, String orgIdentifier,
        String projectIdentifier, String templateId, Map<String, String> templateData);
  }

  @Data
  @SuperBuilder
  public static class CVNGEmailChannel extends CVNGNotificationChannel {
    public final CVNGNotificationChannelType type = CVNGNotificationChannelType.EMAIL;
    List<String> userGroups;
    List<String> recipients;

    @Override
    public NotificationChannel toNotificationChannel(String accountId, String orgIdentifier, String projectIdentifier,
        String templateId, Map<String, String> templateData) {
      return EmailChannel.builder()
          .accountId(accountId)
          .recipients(recipients)
          .userGroups(
              userGroups.stream()
                  .map(e -> CVNGNotificationChannelUtils.getUserGroups(e, accountId, orgIdentifier, projectIdentifier))
                  .collect(Collectors.toList()))
          .team(Team.CV)
          .templateData(templateData)
          .templateId(templateId)
          .build();
    }
  }

  @Data
  @SuperBuilder
  public static class CVNGSlackChannel extends CVNGNotificationChannel {
    public final CVNGNotificationChannelType type = CVNGNotificationChannelType.SLACK;
    List<String> userGroups;
    String webhookUrl;

    @Override
    public NotificationChannel toNotificationChannel(String accountId, String orgIdentifier, String projectIdentifier,
        String templateId, Map<String, String> templateData) {
      return SlackChannel.builder()
          .accountId(accountId)
          .team(Team.CV)
          .templateData(templateData)
          .templateId(templateId)
          .userGroups(
              userGroups.stream()
                  .map(e -> CVNGNotificationChannelUtils.getUserGroups(e, accountId, orgIdentifier, projectIdentifier))
                  .collect(Collectors.toList()))
          .webhookUrls(Lists.newArrayList(webhookUrl))
          .build();
    }
  }

  @Data
  @SuperBuilder
  public static class CVNGPagerDutyChannel extends CVNGNotificationChannel {
    public final CVNGNotificationChannelType type = CVNGNotificationChannelType.PAGERDUTY;
    List<String> userGroups;
    String integrationKey;

    @Override
    public NotificationChannel toNotificationChannel(String accountId, String orgIdentifier, String projectIdentifier,
        String templateId, Map<String, String> templateData) {
      return PagerDutyChannel.builder()
          .accountId(accountId)
          .userGroups(
              userGroups.stream()
                  .map(e -> CVNGNotificationChannelUtils.getUserGroups(e, accountId, orgIdentifier, projectIdentifier))
                  .collect(Collectors.toList()))
          .team(Team.CV)
          .templateId(templateId)
          .integrationKeys(Lists.newArrayList(integrationKey))
          .templateData(templateData)
          .build();
    }
  }

  @Data
  @SuperBuilder
  public static class CVNGMSTeamsChannel extends CVNGNotificationChannel {
    public final CVNGNotificationChannelType type = CVNGNotificationChannelType.MSTEAMS;
    List<String> msTeamKeys;
    List<String> userGroups;

    @Override
    public NotificationChannel toNotificationChannel(String accountId, String orgIdentifier, String projectIdentifier,
        String templateId, Map<String, String> templateData) {
      return MSTeamChannel.builder()
          .msTeamKeys(msTeamKeys)
          .accountId(accountId)
          .team(Team.CV)
          .templateData(templateData)
          .templateId(templateId)
          .userGroups(
              userGroups.stream()
                  .map(e -> CVNGNotificationChannelUtils.getUserGroups(e, accountId, orgIdentifier, projectIdentifier))
                  .collect(Collectors.toList()))
          .build();
    }
  }

  public abstract static class NotificationRuleUpdatableEntity<T extends NotificationRule, D extends NotificationRule>
      implements UpdatableEntity<T, D> {
    protected void setCommonOperations(UpdateOperations<T> updateOperations, D notificationRule) {
      updateOperations.set(NotificationRuleKeys.identifier, notificationRule.getIdentifier())
          .set(NotificationRuleKeys.name, notificationRule.getName())
          .set(NotificationRuleKeys.type, notificationRule.getType())
          .set(NotificationRuleKeys.notificationMethod, notificationRule.getNotificationMethod())
          .inc(NotificationRuleKeys.version);
    }
  }
}
