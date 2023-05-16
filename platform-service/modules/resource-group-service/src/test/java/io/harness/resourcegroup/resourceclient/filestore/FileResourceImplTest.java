/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.resourceclient.filestore;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileResourceImplTest extends CategoryTest {
  @Mock private FileStoreClient fileStoreClient;
  @Inject @InjectMocks FileResourceImpl fileResource;

  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void getType() {
    assertThat(fileResource.getType()).isEqualTo("FILE");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void getValidScopeLevels() {
    assertThat(fileResource.getValidScopeLevels())
        .containsExactlyInAnyOrder(ScopeLevel.PROJECT, ScopeLevel.ORGANIZATION, ScopeLevel.ACCOUNT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void getEventFrameworkEntityType() {
    assertThat(fileResource.getEventFrameworkEntityType().get())
        .isEqualTo(EventsFrameworkMetadataConstants.FILE_ENTITY);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateEmptyResourceList() {
    assertThat(
        fileResource.validate(new ArrayList<>(), Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER)))
        .isEmpty();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    List<String> fileIdentifiers = getRandomPopulatedStringList(30);
    List<FileDTO> fileDTOS = fileIdentifiers.stream()
                                 .map(identifier -> FileDTO.builder().identifier(identifier).build())
                                 .collect(Collectors.toList());

    Call call = Mockito.mock(Call.class);
    doReturn(Response.success(PageResponse.<FileDTO>builder().content(fileDTOS.subList(0, 20)).build()))
        .doReturn(Response.success(PageResponse.<FileDTO>builder().content(fileDTOS.subList(20, 30)).build()))
        .when(call)
        .execute();

    doReturn(call).when(fileStoreClient).listFilesAndFolders(any(), any(), any(), anyList(), anyInt(), anyInt());

    List<Boolean> validateResults =
        fileResource.validate(fileIdentifiers, Scope.of(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER));
    verify(fileStoreClient, times(2)).listFilesAndFolders(any(), any(), any(), anyList(), anyInt(), anyInt());
    assertThat(validateResults.size()).isEqualTo(30);

    for (Boolean validateResult : validateResults) {
      if (!validateResult) {
        fail("All file identifiers should match provided identifiers");
      }
    }
  }

  private List<String> getRandomPopulatedStringList(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> RandomStringUtils.random(5, true, true))
        .collect(Collectors.toList());
  }
}
