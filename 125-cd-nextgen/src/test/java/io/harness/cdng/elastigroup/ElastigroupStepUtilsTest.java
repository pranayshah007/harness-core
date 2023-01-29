/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.exception.InvalidRequestException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.logging.LogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ElastigroupStepUtilsTest extends CDNGTestBase {
  static String CONTENT = "content";

  @Mock private EngineExpressionService engineExpressionService;
  @Mock private FileStoreService fileStoreService;

  @Mock LogCallback logCallback;

  @InjectMocks private ElastigroupStepUtils elastigroupStepUtils;

  @Before
  public void setup() {
    doNothing().when(logCallback).saveExecutionLog(anyString());

    FileStoreNodeDTO mockFileStoreNodeDTO = FileNodeDTO.builder().content(CONTENT).build();
    doReturn(Optional.of(mockFileStoreNodeDTO))
        .when(fileStoreService)
        .getWithChildrenByPath(anyString(), anyString(), anyString(), anyString(), anyBoolean());
  }

  @Test
  @Owner(developers = {VITALIE})
  @Category(UnitTests.class)
  public void fetchFileFromHarnessStoreTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();

    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(Arrays.asList("file1.json"))).build();

    String result = elastigroupStepUtils.fetchFileFromHarnessStore(ambiance, harnessStore, "", logCallback);
    assertThat(result).isEqualTo(CONTENT);
  }

  @Test
  @Owner(developers = {VITALIE})
  @Category(UnitTests.class)
  public void fetchFileFromHarnessStoreFailsTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();

    HarnessStore harnessStore =
        HarnessStore.builder().files(ParameterField.createValueField(Arrays.asList(""))).build();

    assertThatThrownBy(() -> elastigroupStepUtils.fetchFileFromHarnessStore(ambiance, harnessStore, "", logCallback))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("File reference cannot be null or empty");
  }
}
