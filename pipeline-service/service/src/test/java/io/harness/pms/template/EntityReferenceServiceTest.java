/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.template;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.pipeline.PipelineSetupUsageHelper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.creation.PlanCreatorServiceInfo;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDC)
public class EntityReferenceServiceTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String pipelineTemplateYaml = "pipeline:\n"
      + "  identifier: p2\n"
      + "  name: pipeline2\n"
      + "  template:\n"
      + "    templateRef: template1\n"
      + "    versionLabel: v1\n";

  Map<String, PlanCreatorServiceInfo> services = new HashMap<>();

  FilterCreatorMergeService filterCreatorMergeService;
  EntityReferenceService entityReferenceService;
  @Mock PmsSdkHelper pmsSdkHelper;
  @Mock PipelineSetupUsageHelper pipelineSetupUsageHelper;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;
  @Mock PMSPipelineTemplateHelper pmsPipelineTemplateHelper;
  @Mock GitSyncSdkService gitSyncSdkService;
  @Mock PrincipalInfoHelper infoHelper;
  @Mock TriggeredByHelper triggeredByHelper;

  @Before
  public void setup() {
    filterCreatorMergeService = spy(new FilterCreatorMergeService(pmsSdkHelper, pipelineSetupUsageHelper,
        pmsGitSyncHelper, pmsPipelineTemplateHelper, gitSyncSdkService, infoHelper, triggeredByHelper));
    entityReferenceService = new EntityReferenceService(filterCreatorMergeService);
    services.put("cd", new PlanCreatorServiceInfo(new HashMap<>(), null));
    when(filterCreatorMergeService.getServices()).thenReturn(services);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGetReferences() throws IOException {
    EntityReferenceRequest entityReferenceRequest = EntityReferenceRequest.newBuilder()
                                                        .setYaml(pipelineTemplateYaml)
                                                        .setAccountIdentifier(ACCOUNT_ID)
                                                        .setOrgIdentifier(ORG_ID)
                                                        .setProjectIdentifier(PROJECT_ID)
                                                        .build();

    EntityReferenceResponse response = entityReferenceService.getReferences(entityReferenceRequest);
    assertThat(!response.getReferredEntitiesList().isEmpty());

    ArgumentCaptor<Map> servicesCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Dependencies> dependenciesCaptor = ArgumentCaptor.forClass(Dependencies.class);
    ArgumentCaptor<SetupMetadata> setupMetadataArgumentCaptor = ArgumentCaptor.forClass(SetupMetadata.class);
    verify(filterCreatorMergeService, times(1))
        .obtainFiltersRecursively(servicesCaptor.capture(), dependenciesCaptor.capture(), eq(new HashMap<>()),
            setupMetadataArgumentCaptor.capture());
    assertThat(servicesCaptor.getValue()).isEqualTo(services);
    assertThat(dependenciesCaptor.getValue()).isNotNull();
    SetupMetadata setupMetadata = setupMetadataArgumentCaptor.getValue();
    assertThat(setupMetadata).isNotNull();
    assertThat(setupMetadata.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(setupMetadata.getOrgId()).isEqualTo(ORG_ID);
    assertThat(setupMetadata.getProjectId()).isEqualTo(PROJECT_ID);
  }
}