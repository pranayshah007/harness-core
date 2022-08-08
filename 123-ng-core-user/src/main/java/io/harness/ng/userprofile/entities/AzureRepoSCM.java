/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.userprofile.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azurerepoconnector.AzureRepoAuthentication;
import io.harness.connector.mappers.azuremapper.AzureDTOToEntity;
import io.harness.connector.mappers.azuremapper.AzureEntityToDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.ng.userprofile.commons.AzureRepoSCMDTO;
import io.harness.ng.userprofile.commons.SCMType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "AzureDevOpsSCMKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("io.harness.ng.userprofile.entities.AzureDevOpsSCM")
public class AzureRepoSCM extends SourceCodeManager {
  GitAuthType authType;
  AzureRepoAuthentication authenticationDetails;

  @Override
  public SCMType getType() {
    return SCMType.AZURE_REPO;
  }

  public static class AzureRepoSCMMapper extends SourceCodeManagerMapper<AzureRepoSCMDTO, AzureRepoSCM> {
    @Override
    public AzureRepoSCM toSCMEntity(AzureRepoSCMDTO sourceCodeManagerDTO) {
      AzureRepoSCM azureRepoSCM = AzureRepoSCM.builder()
                                      .authType(sourceCodeManagerDTO.getAuthentication().getAuthType())
                                      .authenticationDetails(AzureDTOToEntity.buildAuthenticationDetails(
                                          sourceCodeManagerDTO.getAuthentication().getAuthType(),
                                          sourceCodeManagerDTO.getAuthentication().getCredentials()))
                                      .build();
      setCommonFieldsEntity(azureRepoSCM, sourceCodeManagerDTO);
      return azureRepoSCM;
    }

    @Override
    public AzureRepoSCMDTO toSCMDTO(@NotNull AzureRepoSCM sourceCodeManager) {
      AzureRepoSCMDTO azureRepoSCMDTO =
          AzureRepoSCMDTO.builder()
              .authentication(AzureEntityToDTO.buildAzureAuthentication(
                  sourceCodeManager.getAuthType(), sourceCodeManager.getAuthenticationDetails()))
              .build();
      setCommonFieldsDTO(sourceCodeManager, azureRepoSCMDTO);
      return azureRepoSCMDTO;
    }
  }
}
