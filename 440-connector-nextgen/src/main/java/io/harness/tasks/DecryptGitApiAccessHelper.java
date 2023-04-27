/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.tasks;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.gitapi.DecryptGitAPIAccessTaskResponse;
import io.harness.delegate.beans.gitapi.DecryptGitAPiAccessTaskParams;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.dtos.gitAccess.GitAccessDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.beans.TaskType;
import software.wings.service.impl.security.NGEncryptorService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(DX)
public class DecryptGitApiAccessHelper {
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private EncryptionHelper encryptionHelper;
  @Inject private NGEncryptorService ngEncryptorService;

  public ScmConnector decryptScmApiAccess(
      ScmConnector scmConnector, String accountId, String projectIdentifier, String orgIdentifier) {
    final BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                          .accountIdentifier(accountId)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .build();
    final DecryptableEntity apiAccessDecryptableEntity =
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector);
    List<EncryptedDataDetail> encryptedDataDetailsForAPIAccess =
        getEncryptedDataDetailsForAPIAccess(apiAccessDecryptableEntity, baseNGAccess);
    ngEncryptorService.decryptEncryptedDetails(apiAccessDecryptableEntity, encryptedDataDetailsForAPIAccess, accountId);
    return scmConnector;
  }

  public void decryptGitAccessDTO(GitAccessDTO gitAccessDTO) {
    final BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                          .accountIdentifier(gitAccessDTO.getTokenScope().getAccountIdentifier())
                                          .orgIdentifier(gitAccessDTO.getTokenScope().getOrgIdentifier())
                                          .projectIdentifier(gitAccessDTO.getTokenScope().getProjectIdentifier())
                                          .build();

    List<EncryptedDataDetail> encryptedDataDetailsForAPIAccess =
        getEncryptedDataDetailsForAPIAccess(gitAccessDTO, baseNGAccess);
    ngEncryptorService.decryptEncryptedDetails(
        gitAccessDTO, encryptedDataDetailsForAPIAccess, gitAccessDTO.getTokenScope().getAccountIdentifier());
  }

  private ScmConnector executeDecryptionTask(ScmConnector scmConnector, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<EncryptedDataDetail> encryptedDataDetailsForAPIAccess) {
    DecryptGitAPiAccessTaskParams apiAccessTaskParams = DecryptGitAPiAccessTaskParams.builder()
                                                            .scmConnector(scmConnector)
                                                            .encryptedDataDetails(encryptedDataDetailsForAPIAccess)
                                                            .build();
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountIdentifier, orgIdentifier, projectIdentifier);
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(accountIdentifier)
                                                  .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
                                                  .taskType(TaskType.NG_DECRYT_GIT_API_ACCESS_TASK.name())
                                                  .taskParameters(apiAccessTaskParams)
                                                  .executionTimeout(Duration.ofMinutes(2))
                                                  .build();
    DelegateResponseData responseData = null;
    try {
      responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException e) {
      throw new DelegateNotAvailableException("Encountered error while decrypting secrets", WingsException.USER);
    } catch (Exception e) {
      log.error("Encountered error while decrypting api access", e);
    }
    if (responseData instanceof ErrorNotifyResponseData || responseData instanceof RemoteMethodReturnValueData) {
      log.error("Error decrypting the credentials, the responseData returned from delegate: {}", responseData);
      throw new UnexpectedException("Error while decrypting api access");
    }
    DecryptGitAPIAccessTaskResponse gitConnectorResponse = (DecryptGitAPIAccessTaskResponse) responseData;
    return gitConnectorResponse.getScmConnector();
  }

  private List<EncryptedDataDetail> getEncryptedDataDetailsForAPIAccess(
      DecryptableEntity decryptableEntity, NGAccess ngAccess) {
    return encryptionHelper.getEncryptionDetail(decryptableEntity, ngAccess.getAccountIdentifier(),
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }

  public DecryptableEntity decryptEncryptionDetails(
      DecryptableEntity decryptableEntity, List<EncryptedDataDetail> encryptedDataDetails, String accountId) {
    return ngEncryptorService.decryptEncryptedDetails(decryptableEntity, encryptedDataDetails, accountId);
  }
}
