/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.ng.core.service.dto.ServiceImportRequestDTO;
import io.harness.template.beans.TemplateImportRequestDTO;

@OwnedBy(HarnessTeam.CDC)
public interface ServiceGitXService {
    boolean isNewGitXEnabled(String accountIdentifier, String orgIdentifier, String projectIdentifier);

    String checkForFileUniquenessAndGetRepoURL(String accountIdentifier, String orgIdentifier, String projectIdentifier,
                                               String templateIdentifier, boolean isForceImport);

    String importServiceFromRemote(String accountIdentifier, String orgIdentifier, String projectIdentifier);

    void performImportFlowYamlValidations(String orgIdentifier, String projectIdentifier, String serviceIdentifier,
                                          ServiceImportRequestDTO serviceImportRequest, String importedService);

}
