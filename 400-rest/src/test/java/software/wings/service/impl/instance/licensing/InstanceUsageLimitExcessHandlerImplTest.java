/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.licensing;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.alert.AlertType;
import software.wings.service.impl.AlertServiceImpl;
import software.wings.service.intfc.instance.licensing.InstanceUsageLimitChecker;
import software.wings.service.intfc.instance.licensing.InstanceUsageLimitExcessHandler;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceUsageLimitExcessHandlerImplTest extends WingsBaseTest {
  @Mock private InstanceUsageLimitChecker limitChecker;
  @Mock private AlertServiceImpl alertService;

  @InjectMocks @Inject private InstanceUsageLimitExcessHandler usageLimitExcessHandler;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAlertRaised() {
    String accountId = "some-account-id";
    long percentLimit = 90L;

    when(limitChecker.isWithinLimit(accountId, percentLimit, 10D)).thenReturn(false);
    usageLimitExcessHandler.handle(accountId, GLOBAL_APP_ID, 10D);
    verify(alertService)
        .openAlert(accountId, GLOBAL_APP_ID, AlertType.INSTANCE_USAGE_APPROACHING_LIMIT,
            InstanceUsageLimitExcessHandlerImpl.createAlertData(accountId, percentLimit));
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAlertIsClosedWhenWithinLimit() {
    String accountId = "some-account-id";
    long percentLimit = 90L;

    when(limitChecker.isWithinLimit(accountId, percentLimit, 10D)).thenReturn(true);
    usageLimitExcessHandler.handle(accountId, GLOBAL_APP_ID, 10D);
    verify(alertService)
        .closeAlert(accountId, GLOBAL_APP_ID, AlertType.INSTANCE_USAGE_APPROACHING_LIMIT,
            InstanceUsageLimitExcessHandlerImpl.createAlertData(accountId, percentLimit));
  }
}
