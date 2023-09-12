/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.service;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.CreateGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.DeleteGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.GetGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.ListGitXWebhookResponseDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookCriteriaDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookRequestDTO;
import io.harness.gitsync.gitxwebhooks.dtos.UpdateGitXWebhookResponseDTO;

import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@OwnedBy(HarnessTeam.PIPELINE)
public interface GitXWebhookService {
  CreateGitXWebhookResponseDTO createGitXWebhook(CreateGitXWebhookRequestDTO createGitXWebhookRequestDTO);

  Optional<GetGitXWebhookResponseDTO> getGitXWebhook(GetGitXWebhookRequestDTO getGitXWebhookRequestDTO);

  UpdateGitXWebhookResponseDTO updateGitXWebhook(UpdateGitXWebhookCriteriaDTO updateGitXWebhookCriteriaDTO,
      UpdateGitXWebhookRequestDTO updateGitXWebhookRequestDTO);

  ListGitXWebhookResponseDTO listGitXWebhooks(ListGitXWebhookRequestDTO listGitXWebhookRequestDTO);

  DeleteGitXWebhookResponseDTO deleteGitXWebhook(DeleteGitXWebhookRequestDTO deleteGitXWebhookRequestDTO);
}
