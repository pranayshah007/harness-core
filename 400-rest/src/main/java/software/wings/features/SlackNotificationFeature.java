/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.features;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.structure.EmptyPredicate;

import software.wings.beans.security.UserGroup;
import software.wings.features.api.FeatureRestrictions;
import software.wings.features.api.Usage;
import software.wings.features.utils.NotificationUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@OwnedBy(PL)
@Singleton
public class SlackNotificationFeature extends AbstractNotificationFeature {
  public static final String FEATURE_NAME = "SLACK_NOTIFICATION";

  private final UserGroupService userGroupService;

  @Inject
  public SlackNotificationFeature(AccountService accountService, FeatureRestrictions featureRestrictions,
      WorkflowService workflowService, UserGroupService userGroupService) {
    super(accountService, featureRestrictions, workflowService);
    this.userGroupService = userGroupService;
  }

  @Override
  public String getFeatureName() {
    return FEATURE_NAME;
  }

  // Get usages of Slack under user groups
  @Override
  protected List<Usage> getUsages(String accountId) {
    return getUserGroups(accountId)
        .stream()
        .filter(SlackNotificationFeature::hasSlack)
        .map(NotificationUtils::asUsage)
        .collect(Collectors.toList());
  }

  private static boolean hasSlack(UserGroup userGroup) {
    return userGroup.getNotificationSettings() != null && userGroup.getNotificationSettings().getSlackConfig() != null
        && !EmptyPredicate.isEmpty(userGroup.getNotificationSettings().getSlackConfig().getOutgoingWebhookUrl());
  }

  private PageResponse<UserGroup> getUserGroups(String accountId) {
    return userGroupService.list(accountId, new PageRequest<>(), false, null, null, false);
  }
}
