/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.dto.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.FILIP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filestore.dto.filter.FilesFilterProperties;
import io.harness.filestore.dto.filter.FilesFilterPropertiesDTO;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterPropertiesDTO;
import io.harness.filter.entity.FilterProperties;
import io.harness.ng.core.dto.EmbeddedUserDetailsDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.rule.Owner;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class FilesFilterPropertiesMapperTest {
  private FilesFilterPropertiesMapper mapper = new FilesFilterPropertiesMapper();

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void toEntityTest() {
    EmbeddedUserDetailsDTO createdBy = EmbeddedUserDetailsDTO.builder().name("name").email("email@test.com").build();

    FilesFilterPropertiesDTO filter =
        FilesFilterPropertiesDTO.builder().fileUsage(FileUsage.CONFIG).createdBy(createdBy).build();

    FilterProperties filterProperties = mapper.toEntity(filter);

    assertThat(filterProperties)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(FilesFilterProperties.class))
        .extracting(
            FilesFilterProperties::getType, FilesFilterProperties::getFileUsage, FilesFilterProperties::getCreatedBy)
        .containsExactly(FilterType.FILESTORE, FileUsage.CONFIG, createdBy);
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void writeDtoTest() {
    EmbeddedUserDetailsDTO createdBy = EmbeddedUserDetailsDTO.builder().name("name").email("email@test.com").build();

    FilesFilterProperties filter =
        FilesFilterProperties.builder().fileUsage(FileUsage.CONFIG).createdBy(createdBy).build();

    FilterPropertiesDTO filterProperties = mapper.writeDTO(filter);

    assertThat(filterProperties)
        .isNotNull()
        .asInstanceOf(InstanceOfAssertFactories.type(FilesFilterPropertiesDTO.class))
        .extracting(FilesFilterPropertiesDTO::getFilterType, FilesFilterPropertiesDTO::getFileUsage,
            FilesFilterPropertiesDTO::getCreatedBy)
        .containsExactly(FilterType.FILESTORE, FileUsage.CONFIG, createdBy);
  }
}
