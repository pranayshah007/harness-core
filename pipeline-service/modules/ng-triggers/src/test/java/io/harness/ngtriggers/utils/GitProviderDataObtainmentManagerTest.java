/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.scm.WebhookPayloadData;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitProviderDataObtainmentManagerTest extends CategoryTest {
  @Mock private AwsCodeCommitDataObtainer awsCodeCommitDataObtainer;
  @Mock private SCMDataObtainer scmDataObtainer;
  private GitProviderDataObtainmentManager gitProviderDataObtainmentManager;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    Map<String, GitProviderBaseDataObtainer> obtainerMap = new HashMap<>();
    obtainerMap.put(WebhookSourceRepo.AWS_CODECOMMIT.name(), awsCodeCommitDataObtainer);
    obtainerMap.put(WebhookSourceRepo.AZURE_REPO.name(), scmDataObtainer);
    obtainerMap.put(WebhookSourceRepo.GITHUB.name(), scmDataObtainer);
    obtainerMap.put(WebhookSourceRepo.BITBUCKET.name(), scmDataObtainer);
    obtainerMap.put(WebhookSourceRepo.GITLAB.name(), scmDataObtainer);
    gitProviderDataObtainmentManager = new GitProviderDataObtainmentManager(obtainerMap);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAcquireProviderDataAwsCodeCommit() {
    List<TriggerDetails> triggers = Collections.emptyList();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.AWS_CODECOMMIT.name()).build())
                    .build())
            .build();
    doNothing().when(awsCodeCommitDataObtainer).acquireProviderData(any(), any());
    gitProviderDataObtainmentManager.acquireProviderData(filterRequestData, Collections.emptyList());
    verify(awsCodeCommitDataObtainer, times(1)).acquireProviderData(filterRequestData, triggers);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testAcquireProviderDataScm() {
    List<TriggerDetails> triggers = Collections.emptyList();
    FilterRequestData filterRequestData =
        FilterRequestData.builder()
            .webhookPayloadData(
                WebhookPayloadData.builder()
                    .originalEvent(
                        TriggerWebhookEvent.builder().sourceRepoType(WebhookSourceRepo.GITHUB.name()).build())
                    .build())
            .build();
    doNothing().when(scmDataObtainer).acquireProviderData(any(), any());
    gitProviderDataObtainmentManager.acquireProviderData(filterRequestData, Collections.emptyList());
    verify(scmDataObtainer, times(1)).acquireProviderData(filterRequestData, triggers);
  }
}
