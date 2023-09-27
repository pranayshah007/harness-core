/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitxwebhook;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@HarnessRepo
@OwnedBy(HarnessTeam.PIPELINE)
public interface GitXWebhookRepository extends CrudRepository<GitXWebhook, String>, GitXWebhookRepositoryCustom {
  List<GitXWebhook> findByAccountIdentifierAndIdentifier(String accountIdentifier, String identifier);

  List<GitXWebhook> findByAccountIdentifierAndRepoName(String accountIdentifier, String repoName);
}
