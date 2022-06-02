/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filestore.entities.NGFile.builder;
import static io.harness.rule.OwnerRule.IVAN;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.DuplicateFieldException;
import io.harness.filestore.dto.mapper.FileDTOMapper;
import io.harness.filestore.entities.NGFile;
import io.harness.filestore.service.impl.FileFailsafeServiceImpl;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileFailsafeServiceImplTest extends CategoryTest {
  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private FileFailsafeServiceImpl fileFailsafeService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndPublish() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    FileDTO fileDTO = FileDTOMapper.getFileDTOFromNGFile(ngFile);
    when(transactionTemplate.execute(any())).thenReturn(fileDTO);
    FileDTO savedFileDTO = fileFailsafeService.saveAndPublish(ngFile);

    assertThat(savedFileDTO).isNotNull();
    assertThat(savedFileDTO).isEqualTo(fileDTO);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveAndPublishWithException() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    when(transactionTemplate.execute(any()))
        .thenThrow(
            new DuplicateKeyException(format("The entity with %s identifier already exists", ngFile.getIdentifier())));

    assertThatThrownBy(() -> fileFailsafeService.saveAndPublish(ngFile))
        .isInstanceOf(DuplicateFieldException.class)
        .hasMessage(format("Try using another identifier, [%s] already exists", ngFile.getIdentifier()));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testUpdateAndPublish() {
    NGFile oldNgFile = createNgFile("oldFileName", "oldIdentifier");
    NGFile newNgFile = createNgFile("newFileName", "newIdentifier");
    FileDTO fileDTO = FileDTOMapper.getFileDTOFromNGFile(newNgFile);
    when(transactionTemplate.execute(any())).thenReturn(fileDTO);
    FileDTO updatedFileDTO = fileFailsafeService.updateAndPublish(oldNgFile, newNgFile);

    assertThat(updatedFileDTO).isNotNull();
    assertThat(updatedFileDTO).isEqualTo(fileDTO);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteAndPublish() {
    NGFile ngFile = createNgFile("fileName", "identifier");
    when(transactionTemplate.execute(any())).thenReturn(true);
    boolean deletedFileDTO = fileFailsafeService.deleteAndPublish(ngFile);

    assertThat(deletedFileDTO).isTrue();
  }

  private NGFile createNgFile(String name, String identifier) {
    return builder().type(NGFileType.FILE).name(name).description("Description").identifier(identifier).build();
  }
}
