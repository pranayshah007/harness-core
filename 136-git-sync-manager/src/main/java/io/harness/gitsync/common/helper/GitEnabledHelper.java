/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.EntityType.CONNECTORS;
import static io.harness.EntityType.PIPELINES;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.EntityType;
import io.harness.Microservice;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.GitToHarnessServiceGrpc;
import io.harness.gitsync.ResetGitSyncSDKCacheRequest;
import io.harness.gitsync.ResetGitSyncSDKCacheResponse;
import io.harness.gitsync.common.dtos.ConnectivityMode;
import io.harness.gitsync.common.dtos.GitEnabledDTO;
import io.harness.gitsync.common.dtos.GitEnabledDTO.GitEnabledDTOBuilder;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.YamlGitConfigService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class GitEnabledHelper {
  private final GitSyncSettingsService gitSyncSettingsService;
  private final YamlGitConfigService yamlGitConfigService;
  private Map<EntityType, Microservice> entityTypeMicroserviceMap;
  private Map<Microservice, GitToHarnessServiceGrpc.GitToHarnessServiceBlockingStub> gitToHarnessServiceGrpcClient;

  public GitEnabledDTO getGitEnabledDTO(
      String projectIdentifier, String organizationIdentifier, String accountIdentifier) {
    final boolean gitSyncEnabled =
        yamlGitConfigService.isGitSyncEnabled(accountIdentifier, organizationIdentifier, projectIdentifier);
    final GitEnabledDTOBuilder gitEnabledDTOBuilder = GitEnabledDTO.builder().isGitSyncEnabled(gitSyncEnabled);
    final Optional<GitSyncSettingsDTO> gitSyncSettingsDTO =
        gitSyncSettingsService.get(accountIdentifier, organizationIdentifier, projectIdentifier);
    if (gitSyncEnabled) {
      final ConnectivityMode connectivityMode = gitSyncSettingsDTO.filter(settings -> !settings.isExecuteOnDelegate())
                                                    .map(settings -> ConnectivityMode.MANAGER)
                                                    .orElse(ConnectivityMode.DELEGATE);
      gitEnabledDTOBuilder.connectivityMode(connectivityMode);
      gitSyncSettingsDTO.ifPresent(
          settingsDTO -> gitEnabledDTOBuilder.isGitSyncEnabledOnlyForFF(settingsDTO.isEnabledOnlyForFF()));
    } else {
      gitSyncSettingsDTO.ifPresent(syncSettingsDTO
          -> gitEnabledDTOBuilder.isGitSimplificationEnabled(syncSettingsDTO.isGitSimplificationEnabled()));
    }
    return gitEnabledDTOBuilder.build();
  }

  public void resetGitSyncSDKCache(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<String> errors = new ArrayList<>();
    List<EntityType> supportedEntityTypes = Arrays.asList(CONNECTORS, PIPELINES);
    entityTypeMicroserviceMap.forEach((entityType, msvc) -> {
      if (supportedEntityTypes.contains(entityType)) {
        GitToHarnessServiceGrpc.GitToHarnessServiceBlockingStub gitToHarnessServiceBlockingStub =
            gitToHarnessServiceGrpcClient.get(msvc);
        ResetGitSyncSDKCacheResponse response =
            gitToHarnessServiceBlockingStub.resetGitSyncSDKCache(ResetGitSyncSDKCacheRequest.newBuilder()
                                                                     .setAccountIdentifier(accountIdentifier)
                                                                     .setOrgIdentifier(orgIdentifier)
                                                                     .setProjectIdentifier(projectIdentifier)
                                                                     .build());
        if (isNotEmpty(response.getError())) {
          errors.add(response.getError());
        }
      }
    });
    if (!errors.isEmpty()) {
      throw new UnsupportedOperationException(String.join("**||||||||**", errors));
    }
  }
}
