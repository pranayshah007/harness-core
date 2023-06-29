/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.manifest.ManifestHelper.values_filename;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ACHYUTH;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.delegate.task.helm.HelmFetchFileConfig;
import io.harness.delegate.task.localstore.LocalStoreFetchFilesResult;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.logging.LogCallback;
import io.harness.logging.LoggingInitializer;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sHelmCommonStepHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";

  @Mock private EngineExpressionService engineExpressionService;
  @Mock private LogCallback logCallback;
  @Mock private FileStoreService fileStoreService;
  @Inject @InjectMocks K8sHelmCommonStepHelper k8sHelmCommonStepHelper;
  @Inject K8sHelmCommonStepHelper spyHelmCommonStepHelper;

  private final Ambiance ambiance = Ambiance.newBuilder().build();
  private final NGAccess ngAccess =
      BaseNGAccess.builder().accountIdentifier(ACCOUNT_ID).orgIdentifier(ORG_ID).projectIdentifier(PROJECT_ID).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    LoggingInitializer.initializeLogging();
    spyHelmCommonStepHelper = Mockito.spy(k8sHelmCommonStepHelper);

    doAnswer(invocation -> invocation.getArgument(1))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), anyString(), eq(false));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testHelmChartManifestWithSubChartDefValuesYaml() {
    List<HelmFetchFileConfig> helmFetchFileConfigs = k8sHelmCommonStepHelper.mapHelmChartManifestsToHelmFetchFileConfig(
        "manifest-1", Collections.emptyList(), ManifestType.HelmChart, "charts/sub-chart-1");

    assertThat(helmFetchFileConfigs.get(0).getFilePaths()).contains("charts/sub-chart-1/values.yaml");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesFileContentsAsLocalStoreFetchFilesResultNullValue() {
    ManifestOutcome manifestOutcome =
        K8sManifestOutcome.builder()
            .store(HarnessStore.builder()
                       .files(ParameterField.createValueField(Collections.singletonList("folder-path")))
                       .build())
            .build();

    doReturn(Optional.of(FileNodeDTO.builder().content(null).build()))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, "folder-path/" + values_filename, true);

    LocalStoreFetchFilesResult result = k8sHelmCommonStepHelper.getValuesFileContentsAsLocalStoreFetchFilesResult(
        manifestOutcome, ngAccess, logCallback);
    assertThat(result.getLocalStoreFileContents()).containsExactlyInAnyOrder("");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFileContentsFromManifest() {
    final List<String> fileContents = new ArrayList<>();
    final List<String> scopedFilePathList =
        asList("files/non-empty1.yaml", "files/non-empty2.yaml", "files/empty.yaml", "files/non-empty3.yaml");

    doReturn(Optional.of(FileNodeDTO.builder().content("content1").build()))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, "files/non-empty1.yaml", true);
    doReturn(Optional.of(FileNodeDTO.builder().content("content2").build()))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, "files/non-empty2.yaml", true);
    doReturn(Optional.of(FileNodeDTO.builder().content(null).build()))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, "files/empty.yaml", true);
    doReturn(Optional.of(FileNodeDTO.builder().content("content3").build()))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, "files/non-empty3.yaml", true);

    LocalStoreFetchFilesResult result = k8sHelmCommonStepHelper.getFileContentsFromManifest(
        ngAccess, fileContents, scopedFilePathList, "Values", "id1", logCallback);

    assertThat(result.getLocalStoreFileContents()).containsExactlyInAnyOrder("content1", "content2", "", "content3");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetValuesFileContents() {
    final List<String> fileContents = asList("content1", "content2", null, "content3");

    assertThat(k8sHelmCommonStepHelper.getValuesFileContents(ambiance, fileContents))
        .containsExactlyInAnyOrder("content1", "content2", "content3");
    verify(engineExpressionService).renderExpression(ambiance, "content1", false);
    verify(engineExpressionService).renderExpression(ambiance, "content2", false);
    verify(engineExpressionService).renderExpression(ambiance, "content3", false);
    verify(engineExpressionService, never()).renderExpression(ambiance, null, false);
  }
}
