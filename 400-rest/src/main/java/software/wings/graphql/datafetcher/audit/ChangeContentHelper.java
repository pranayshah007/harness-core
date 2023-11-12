/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.audit;

import static io.harness.TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.event.handler.impl.segment.SegmentHelper;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.Principal;
import software.wings.resources.graphql.TriggeredByType;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ChangeContentHelper {
  @Inject private SegmentHandler segmentHandler;
  @Inject private AccountService accountService;
  @Inject private UserService userService;
  @Inject private SegmentHelper segmentHelper;
  private static final String segmentEvent = "Audit Trail exported";

  public void reportAuditTrailExportToSegment(String accountId, Principal triggeredBy) {
    final Account account = accountService.get(accountId);
    if (account == null || triggeredBy == null) {
      return;
    }
    Map<String, String> properties = new HashMap<String, String>() {
      {
        put("userId", String.format(SEGMENT_DUMMY_ACCOUNT_PREFIX + "%s", accountId));
        put("groupId", accountId);
      }
    };
    Map<String, Boolean> integrations = new HashMap<String, Boolean>() {
      { put(SegmentHandler.Keys.NATERO, Boolean.TRUE); }
    };

    if (triggeredBy.getTriggeredById() != null) {
      if (triggeredBy.getTriggeredByType() == TriggeredByType.USER) {
        User user = userService.get(triggeredBy.getTriggeredById());
        try {
          segmentHandler.reportTrackEvent(account, segmentEvent, user, properties, integrations);
        } catch (Exception e) {
          log.error(String.format(
              "Exception while reporting track event for Audit Trail Export for accountId: %s, exception: %s",
              accountId, e.getMessage()));
        }
      } else if (triggeredBy.getTriggeredByType() == TriggeredByType.API_KEY) {
        segmentHelper.reportTrackEvent(
            String.format(SEGMENT_DUMMY_ACCOUNT_PREFIX + "%s", accountId), segmentEvent, properties, integrations);
      }
    }
  }
}
