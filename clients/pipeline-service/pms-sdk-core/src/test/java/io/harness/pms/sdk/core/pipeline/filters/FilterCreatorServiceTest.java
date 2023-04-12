/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.filters;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.FilterCreationBlobRequest;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.contracts.plan.YamlFieldBlob;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoDecoratorImpl;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.fabric8.utils.Lists;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class FilterCreatorServiceTest extends PmsSdkCoreTestBase {
  private static final String NODE_UUID = "test";

  @Mock PipelineServiceInfoDecoratorImpl serviceInfoDecorator;
  @Mock FilterCreationResponseMerger filterCreationResponseMerger;
  @Mock PmsGitSyncHelper pmsGitSyncHelper;

  @InjectMocks FilterCreatorService filterCreatorService;

  private YamlField pipelineField;
  String yamlContent;
  @Before
  public void setup() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    pipelineField = YamlUtils.extractPipelineField(YamlUtils.injectUuid(yamlContent));
    when(serviceInfoDecorator.getFilterJsonCreators()).thenReturn(Arrays.asList(new NoopFilterJsonCreator()));
    doNothing().when(filterCreationResponseMerger).mergeFilterCreationResponse(any(), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetInitialDependencies() throws IOException {
    Map<String, YamlFieldBlob> yamlFieldBlobMap = new HashMap<>();
    yamlFieldBlobMap.put(NODE_UUID, pipelineField.toFieldBlob());
    Map<String, YamlField> yamlFieldMap = filterCreatorService.getInitialDependencies(yamlFieldBlobMap);
    assertThat(yamlFieldMap.get(NODE_UUID)).isEqualTo(pipelineField);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMergeResponses() {
    FilterCreationResponse finalCreationResponse =
        FilterCreationResponse.builder().stageCount(10).stageNames(Lists.newArrayList("stage1")).build();
    FilterCreationResponse filterCreationResponse =
        FilterCreationResponse.builder().stageCount(10).stageNames(Lists.newArrayList("stage2")).build();

    filterCreatorService.mergeResponses(finalCreationResponse, filterCreationResponse, Dependencies.newBuilder());

    assertThat(finalCreationResponse.getStageCount()).isEqualTo(20);
    assertThat(finalCreationResponse.getStageNames()).isEqualTo(Arrays.asList("stage1", "stage2"));
    verify(filterCreationResponseMerger).mergeFilterCreationResponse(finalCreationResponse, filterCreationResponse);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testProcessNodeInternal() {
    FilterCreationBlobRequest request = FilterCreationBlobRequest.newBuilder().build();
    assertThat(filterCreatorService.processNodeInternal(SetupMetadata.newBuilder().build(), pipelineField, request))
        .isEqualTo(FilterCreationResponse.builder().build());
    verify(serviceInfoDecorator).getFilterJsonCreators();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testProcessNodesRecursively() throws IOException {
    Dependencies initialDependencies = Dependencies.newBuilder()
                                           .setYaml(YamlUtils.injectUuid(yamlContent))
                                           .putDependencies(NODE_UUID, pipelineField.getYamlPath())
                                           .build();
    FilterCreationBlobRequest request = FilterCreationBlobRequest.newBuilder().build();
    FilterCreationResponse filterCreationResponse = filterCreatorService.processNodesRecursively(
        initialDependencies, SetupMetadata.newBuilder().build(), FilterCreationResponse.builder().build(), request);
    assertThat(filterCreationResponse).isNotNull();
    assertThat(filterCreationResponse.getStageNames()).isEmpty();
  }
}
