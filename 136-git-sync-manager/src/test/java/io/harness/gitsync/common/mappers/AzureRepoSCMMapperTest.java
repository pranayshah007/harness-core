/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.mappers;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoApiAccess;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoTokenApiAccess;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.beans.AzureRepoSCM;
import io.harness.gitsync.common.dtos.AzureRepoSCMDTO;
import io.harness.gitsync.common.dtos.AzureRepoSCMRequestDTO;
import io.harness.gitsync.common.dtos.AzureRepoSCMResponseDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class AzureRepoSCMMapperTest extends GitSyncTestBase {
  @Inject private Map<SCMType, UserSourceCodeManagerMapper> scmMapBinder;
  AzureRepoSCMMapper azureRepoSCMMapper;
  AzureRepoApiAccessSpecDTO azureRepoApiAccessSpecDTO;
  AzureRepoApiAccessDTO azureRepoApiAccessDTO;
  AzureRepoSCMDTO azureRepoSCMDTO;
  AzureRepoApiAccess azureRepoApiAccess;
  AzureRepoSCM azureRepoSCM;

  @Before
  public void setup() {
    azureRepoSCMMapper = (AzureRepoSCMMapper) scmMapBinder.get(SCMType.AZURE_REPO);
    azureRepoApiAccessSpecDTO =
        AzureRepoTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build())

            .build();
    azureRepoApiAccessDTO =
        azureRepoApiAccessDTO.builder().type(AzureRepoApiAccessType.TOKEN).spec(azureRepoApiAccessSpecDTO).build();
    azureRepoSCMDTO = azureRepoSCMDTO.builder().apiAccess(azureRepoApiAccessDTO).build();
    azureRepoApiAccess = AzureRepoTokenApiAccess.builder().tokenRef("account.tokenRef").build();
    azureRepoSCM = azureRepoSCM.builder()
                       .apiAccessType(AzureRepoApiAccessType.TOKEN)
                       .azureRepoApiAccess(azureRepoApiAccess)
                       .build();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToEntityInternal() {
    assertThatThrownBy(() -> azureRepoSCMMapper.toEntityInternal(azureRepoSCMDTO))
        .isInstanceOf(UnknownEnumTypeException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToDTOInternal() {
    assertThatThrownBy(() -> azureRepoSCMMapper.toDTOInternal(azureRepoSCM))
        .isInstanceOf(UnknownEnumTypeException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToResponseDTOInternal() {
    assertEquals(azureRepoSCMMapper.toResponseDTO(azureRepoSCMDTO),
        AzureRepoSCMResponseDTO.builder().apiAccess(azureRepoApiAccessDTO).build());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToServiceDTOInternal() {
    assertEquals(
        azureRepoSCMMapper.toServiceDTO(AzureRepoSCMRequestDTO.builder().apiAccess(azureRepoApiAccessDTO).build()),
        azureRepoSCMDTO);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToApiAccess() {
    assertThatThrownBy(() -> azureRepoSCMMapper.toApiAccess(azureRepoApiAccessSpecDTO, AzureRepoApiAccessType.TOKEN))
        .isInstanceOf(UnknownEnumTypeException.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testToApiAccessDTO() {
    assertThatThrownBy(() -> azureRepoSCMMapper.toApiAccessDTO(AzureRepoApiAccessType.TOKEN, azureRepoApiAccess))
        .isInstanceOf(UnknownEnumTypeException.class);
  }
}
