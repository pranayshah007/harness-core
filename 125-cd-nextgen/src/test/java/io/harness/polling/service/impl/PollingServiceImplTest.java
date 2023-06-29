/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.service.impl;

import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.dto.PolledResponse;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.polling.bean.ArtifactPolledResponse;
import io.harness.polling.bean.ManifestPolledResponse;
import io.harness.polling.bean.PollingDocument;
import io.harness.polling.bean.PollingType;
import io.harness.polling.bean.artifact.DockerHubArtifactInfo;
import io.harness.repositories.polling.PollingRepository;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PollingServiceImplTest extends CategoryTest {
  @InjectMocks PollingServiceImpl pollingService;
  @Mock PollingRepository pollingRepository;
  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String pollingDocId = "pollingDocId";
  String perpetualTaskId = "perpetualTaskId";
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetPollingInfoForTriggers() {
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();
    PollingInfoForTriggers pollingInfoForTriggers =
        PollingInfoForTriggers.builder()
            .perpetualTaskId(perpetualTaskId)
            .polledResponse(PolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    // For artifacts
    when(pollingRepository.findByUuidAndAccountId(pollingDocId, accountId)).thenReturn(pollingDocument);
    assertThat(pollingService.getPollingInfoForTriggers(accountId, pollingDocId)).isEqualTo(pollingInfoForTriggers);

    // For manifests
    pollingDocument.setPollingType(PollingType.MANIFEST);
    pollingDocument.setPolledResponse(ManifestPolledResponse.builder().allPolledKeys(Set.of("key1")).build());
    when(pollingRepository.findByUuidAndAccountId(pollingDocId, accountId)).thenReturn(pollingDocument);
    assertThat(pollingService.getPollingInfoForTriggers(accountId, pollingDocId)).isEqualTo(pollingInfoForTriggers);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSave() {
    DockerHubArtifactInfo dockerHubArtifactInfo = DockerHubArtifactInfo.builder().connectorRef("connectorRef").build();
    PollingDocument pollingDocument =
        PollingDocument.builder()
            .uuid("uuid")
            .accountId(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pollingType(PollingType.ARTIFACT)
            .perpetualTaskId(perpetualTaskId)
            .signatures(List.of("trigger1"))
            .pollingInfo(dockerHubArtifactInfo)
            .polledResponse(ArtifactPolledResponse.builder().allPolledKeys(Set.of("key1")).build())
            .build();

    doReturn(null)
        .when(pollingRepository)
        .addSubscribersToExistingPollingDoc(accountId, orgId, projectId, PollingType.ARTIFACT, dockerHubArtifactInfo,
            Collections.singletonList("trigger1"));
    doReturn(pollingDocument).when(pollingRepository).save(any());
    pollingService.save(pollingDocument);
    ArgumentCaptor<PollingDocument> pollingDocumentArgumentCaptor = ArgumentCaptor.forClass(PollingDocument.class);
    verify(pollingRepository, times(1)).save(pollingDocumentArgumentCaptor.capture());
    assertThat(pollingDocumentArgumentCaptor.getValue().getUuid()).isNull();
  }
}
