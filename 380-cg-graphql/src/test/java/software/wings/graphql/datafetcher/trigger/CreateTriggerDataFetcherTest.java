/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.trigger.Trigger;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLTriggerPayload;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class CreateTriggerDataFetcherTest extends CategoryTest {
  @Mock TriggerService triggerService;
  @Mock TriggerController triggerController;
  @Mock FeatureFlagService featureFlagService;
  @InjectMocks @Inject CreateTriggerDataFetcher createTriggerDataFetcher = new CreateTriggerDataFetcher(triggerService);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testMutateAndFetch() {
    doReturn(Trigger.builder().build()).when(triggerService).save(null);
    doReturn(null).when(triggerController).prepareTrigger(any(), any());
    doReturn(null).when(triggerController).prepareQLTrigger(any(), any(), any());

    QLTriggerPayload trigger = createTriggerDataFetcher.mutateAndFetch(
        QLCreateOrUpdateTriggerInput.builder().build(), MutationContext.builder().accountId("id").build());
    assertThat(trigger).isNull();
    verify(triggerService, times(1)).save(any());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testMutateAndFetchShouldFailWithoutAuthorizing() {
    doReturn(Trigger.builder().build()).when(triggerService).save(null);
    doReturn(mock(Trigger.class)).when(triggerController).prepareTrigger(any(), any());
    doReturn(null).when(triggerController).prepareQLTrigger(any(), any(), any());
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    doThrow(new WingsException("")).when(triggerService).authorizeSave(any(Trigger.class));

    assertThatThrownBy(()
                           -> createTriggerDataFetcher.mutateAndFetch(QLCreateOrUpdateTriggerInput.builder().build(),
                               MutationContext.builder().accountId("id").build()))
        .isInstanceOf(WingsException.class);
    verify(triggerService, times(0)).save(any());
    verify(triggerService).authorizeSave(any(Trigger.class));
  }
}
