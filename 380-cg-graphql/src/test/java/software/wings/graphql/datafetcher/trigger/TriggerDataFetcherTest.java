/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.beans.FeatureName.SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.User;
import software.wings.beans.Workflow;
import software.wings.beans.trigger.Trigger;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.query.QLTriggerQueryParameters;
import software.wings.graphql.schema.type.trigger.QLTrigger;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.trigger.TriggerAuthHandler;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;
import java.sql.SQLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDC)
public class TriggerDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock TriggerAuthHandler triggerAuthHandler;
  @Mock TriggerService triggerService;
  @Mock FeatureFlagService featureFlagService;
  @Inject @InjectMocks TriggerDataFetcher triggerDataFetcher;
  private Workflow workflow;

  @Before
  public void setup() throws SQLException {
    User user = testUtils.createUser(testUtils.createAccount());
    UserThreadLocal.set(user);

    // Account1
    createAccount(ACCOUNT1_ID, getLicenseInfo());
    createApp(ACCOUNT1_ID, APP1_ID_ACCOUNT1, APP1_ID_ACCOUNT1, TAG_TEAM, TAG_VALUE_TEAM1);
    workflow = createWorkflow(ACCOUNT1_ID, APP1_ID_ACCOUNT1, WORKFLOW1);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testTriggerDataFetcher() {
    createCustomTrigger(
        ACCOUNT1_ID, APP1_ID_ACCOUNT1, TRIGGER_ID1_APP1_ACCOUNT1, TRIGGER_ID1_APP1_ACCOUNT1, workflow.getUuid());
    Mockito.doNothing().when(triggerAuthHandler).authorizeAppAccess(any(), any());
    QLTrigger qlTrigger = triggerDataFetcher.fetch(
        QLTriggerQueryParameters.builder().triggerId(TRIGGER_ID1_APP1_ACCOUNT1).build(), ACCOUNT1_ID);
    assertThat(qlTrigger.getId()).isEqualTo(TRIGGER_ID1_APP1_ACCOUNT1);
    assertThat(qlTrigger.getName()).isEqualTo(TRIGGER_ID1_APP1_ACCOUNT1);

    qlTrigger = triggerDataFetcher.fetch(QLTriggerQueryParameters.builder()
                                             .triggerName(TRIGGER_ID1_APP1_ACCOUNT1)
                                             .applicationId(APP1_ID_ACCOUNT1)
                                             .build(),
        ACCOUNT1_ID);
    assertThat(qlTrigger.getId()).isEqualTo(TRIGGER_ID1_APP1_ACCOUNT1);
    assertThat(qlTrigger.getName()).isEqualTo(TRIGGER_ID1_APP1_ACCOUNT1);

    qlTrigger = triggerDataFetcher.fetch(
        QLTriggerQueryParameters.builder().triggerId(TRIGGER_ID2_APP1_ACCOUNT1).build(), ACCOUNT1_ID);
    assertThat(qlTrigger).isNull();

    try {
      triggerDataFetcher.fetch(
          QLTriggerQueryParameters.builder().triggerId(TRIGGER_ID1_APP1_ACCOUNT1).build(), ACCOUNT2_ID);

      fail("InvalidRequestException expected here");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class);
    }

    try {
      triggerDataFetcher.fetch(
          QLTriggerQueryParameters.builder().triggerName(TRIGGER_ID1_APP1_ACCOUNT1).build(), ACCOUNT1_ID);

      fail("InvalidRequestException expected here");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class).hasMessage("Application Id should not be empty");
    }

    try {
      triggerDataFetcher.fetch(
          QLTriggerQueryParameters.builder().triggerName("").applicationId(APP1_ID_ACCOUNT1).build(), ACCOUNT1_ID);

      fail("InvalidRequestException expected here");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(InvalidRequestException.class).hasMessage("Trigger Name should not be empty");
    }
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testTriggerDataFetcherShouldFailWhenNotAuthorized() {
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_WORKFLOW_RBAC_ON_TRIGGER_RESOURCE), any());
    doThrow(new WingsException("")).when(triggerService).authorizeRead(any(Trigger.class));
    createCustomTrigger(
        ACCOUNT1_ID, APP1_ID_ACCOUNT1, TRIGGER_ID1_APP1_ACCOUNT1, TRIGGER_ID1_APP1_ACCOUNT1, workflow.getUuid());
    Mockito.doNothing().when(triggerAuthHandler).authorizeAppAccess(any(), any());

    assertThatThrownBy(
        ()
            -> triggerDataFetcher.fetch(
                QLTriggerQueryParameters.builder().triggerId(TRIGGER_ID1_APP1_ACCOUNT1).build(), ACCOUNT1_ID))
        .isInstanceOf(WingsException.class);
  }
}
