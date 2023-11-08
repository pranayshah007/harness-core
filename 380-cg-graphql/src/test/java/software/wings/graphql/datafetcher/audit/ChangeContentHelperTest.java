/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.audit;

import static io.harness.TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;
import static io.harness.rule.OwnerRule.XIN;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.graphql.utils.nameservice.NameService.triggeredBy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.Principal;
import software.wings.resources.graphql.TriggeredByType;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ChangeContentHelperTest extends AbstractDataFetcherTestBase {
  @Mock private SegmentHandler segmentHandler;
  @Inject @InjectMocks private ChangeContentHelper changeContentHelper;
  @Mock private AccountService accountService;
  @Mock private UserService userService;
  @Mock private SegmentHelper segmentHelper;

  private Account account;

  private static final String TRIGGERED_BY_ID = "TRIGGERED_BY_ID";

  private Principal apiKeyPrincipal;
  @Before
  public void setUp() {
    account = testUtils.createAccount();
    apiKeyPrincipal =
        Principal.builder().triggeredById(TRIGGERED_BY_ID).triggeredByType(TriggeredByType.API_KEY).build();
  }
  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void shouldReportToSegment() throws URISyntaxException {
    Account account = anAccount()
                          .withCompanyName("company1")
                          .withAccountName("account1")
                          .withAccountKey("ACCOUNT_KEY")
                          .withUuid("accountId")
                          .withLicenseInfo(getLicenseInfo())
                          .build();
    User user = User.Builder.anUser().uuid("userId").build();
    when(accountService.get(account.getUuid())).thenReturn(account);
    Principal triggeredBy =
        Principal.builder().triggeredById("triggeredById").triggeredByType(TriggeredByType.USER).build();
    when(userService.get(triggeredBy.getTriggeredById())).thenReturn(user);
    changeContentHelper.reportAuditTrailExportToSegment("accountId", triggeredBy);
    verify(segmentHandler, times(1))
        .reportTrackEvent(eq(account), eq("Audit Trail exported"), any(User.class), anyMap(), anyMap());
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testSendSegmentReportWithNullUserId() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    when(accountService.get(account.getUuid())).thenReturn(account);
    when(segmentHelper.reportTrackEvent(captor.capture(), any(), any(), any())).thenReturn(true);
    changeContentHelper.reportAuditTrailExportToSegment(account.getUuid(), apiKeyPrincipal);
    verify(segmentHelper, times(1)).reportTrackEvent(captor.capture(), any(), any(), any());
    String identity = captor.getValue();
    assertThat(identity).isEqualTo(SEGMENT_DUMMY_ACCOUNT_PREFIX + account.getUuid());
  }
}
