/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.JOHANNES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.appmanifest.ManifestFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ManifestFileMapperTest extends CategoryTest {
  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void manifestFileDTOListSmokeTest() {
    List<ManifestFile> manifestFiles = Arrays.asList(ManifestFile.builder()
                                                         .fileName("someFileName1")
                                                         .fileContent("someContent1")
                                                         .applicationManifestId("someManifestId1")
                                                         .accountId("someAccountId1")
                                                         .build(),
        ManifestFile.builder()
            .fileName("someFileName2")
            .fileContent("someContent2")
            .applicationManifestId("someManifestId2")
            .accountId("someAccountId2")
            .build());

    List<software.wings.beans.dto.ManifestFile> manifestFileDTOs =
        ManifestFileMapper.manifestFileDTOList(manifestFiles);

    assertThat(manifestFileDTOs).isNotNull();
    assertThat(manifestFileDTOs.size()).isEqualTo(2);
    assertThat(manifestFileDTOs.get(0).getFileName()).isEqualTo(manifestFiles.get(0).getFileName());
    assertThat(manifestFileDTOs.get(0).getFileContent()).isEqualTo(manifestFiles.get(0).getFileContent());
    assertThat(manifestFileDTOs.get(0).getApplicationManifestId())
        .isEqualTo(manifestFiles.get(0).getApplicationManifestId());
    assertThat(manifestFileDTOs.get(0).getAccountId()).isEqualTo(manifestFiles.get(0).getAccountId());
    assertThat(manifestFileDTOs.get(1).getFileName()).isEqualTo(manifestFiles.get(1).getFileName());
    assertThat(manifestFileDTOs.get(1).getFileContent()).isEqualTo(manifestFiles.get(1).getFileContent());
    assertThat(manifestFileDTOs.get(1).getApplicationManifestId())
        .isEqualTo(manifestFiles.get(1).getApplicationManifestId());
    assertThat(manifestFileDTOs.get(1).getAccountId()).isEqualTo(manifestFiles.get(1).getAccountId());
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void manifestFileDTOListForNull() {
    List<software.wings.beans.dto.ManifestFile> result = ManifestFileMapper.manifestFileDTOList(null);
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JOHANNES)
  @Category(UnitTests.class)
  public void manifestFileDTOListForEmptyList() {
    List<software.wings.beans.dto.ManifestFile> result = ManifestFileMapper.manifestFileDTOList(new ArrayList<>());
    assertThat(result).isNotNull();
    assertThat(result.size()).isEqualTo(0);
  }
}
