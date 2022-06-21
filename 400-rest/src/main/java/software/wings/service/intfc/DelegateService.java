/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.ConnectionMode;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateApproval;
import io.harness.delegate.beans.DelegateApprovalResponse;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateDTO;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateInitializationDetails;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateScripts;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.delegate.beans.DelegateTags;
import io.harness.delegate.beans.DelegateUnregisterRequest;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.InvalidRequestException;
import io.harness.validation.Create;

import software.wings.beans.CEDelegateStatus;
import software.wings.beans.DelegateStatus;
import software.wings.service.intfc.ownership.OwnedByAccount;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
@BreakDependencyOn("software.wings.service.intfc.ownership.OwnedByAccount")
public interface DelegateService extends OwnedByAccount {
  PageResponse<Delegate> list(PageRequest<Delegate> pageRequest);

  List<String> getKubernetesDelegateNames(String accountId);

  Set<String> getAllDelegateSelectors(String accountId);

  Set<String> getAllDelegateSelectorsUpTheHierarchy(String accountId, String orgId, String projectId);

  DelegateStatus getDelegateStatus(String accountId);

  DelegateStatus getDelegateStatusWithScalingGroups(String accountId);

  Set<String> retrieveDelegateSelectors(Delegate delegate, boolean fetchFromCache);

  List<String> getAvailableVersions(String accountId);

  Double getConnectedRatioWithPrimary(String targetVersion, String accountId);

  Double getConnectedDelegatesRatio(String version, String accountId);

  Map<String, List<String>> getActiveDelegatesPerAccount(String targetVersion);

  DelegateSetupDetails validateKubernetesYaml(String accountId, DelegateSetupDetails delegateSetupDetails);

  File generateKubernetesYaml(String accountId, DelegateSetupDetails delegateSetupDetails, String managerHost,
      String verificationServiceUrl, MediaType fileFormat) throws IOException;

  Delegate update(@Valid Delegate delegate);

  Delegate updateTags(@Valid Delegate delegate);

  Delegate updateDescription(String accountId, String delegateId, String newDescription);

  Delegate updateApprovalStatus(String accountId, String delegateId, DelegateApproval action)
      throws InvalidRequestException;

  Delegate updateScopes(@Valid Delegate delegate);

  DelegateScripts getDelegateScriptsNg(String accountId, String version, String managerHost, String verificationHost,
      String delegateType) throws IOException;

  DelegateScripts getDelegateScripts(String accountId, String version, String managerHost, String verificationHost,
      String delegateName) throws IOException;

  String getLatestDelegateVersion(String accountId);

  File downloadScripts(String managerHost, String verificationServiceUrl, String accountId, String delegateName,
      String delegateProfile, String tokenName) throws IOException;

  File downloadDocker(String managerHost, String verificationServiceUrl, String accountId, String delegateName,
      String delegateProfile, String tokenName) throws IOException;

  File downloadKubernetes(String managerHost, String verificationServiceUrl, String accountId, String delegateName,
      String delegateProfile, String tokenName) throws IOException;

  File downloadCeKubernetesYaml(String managerHost, String verificationUrl, String accountId, String delegateName,
      String delegateProfile, String tokenName) throws IOException;

  File downloadECSDelegate(String managerHost, String verificationUrl, String accountId, boolean awsVpcMode,
      String hostname, String delegateGroupName, String delegateProfile, String tokenName) throws IOException;
  Delegate add(Delegate delegate);

  void delete(String accountId, String delegateId);

  void retainOnlySelectedDelegatesAndDeleteRest(String accountId, List<String> delegatesToRetain);

  void deleteDelegateGroup(String accountId, String delegateGroupId);

  void deleteDelegateGroupV2(String accountId, String orgId, String projectId, String identifier);

  DelegateRegisterResponse register(@Valid Delegate delegate);

  DelegateRegisterResponse register(@Valid DelegateParams delegateParams);

  void unregister(String accountId, DelegateUnregisterRequest request);

  void registerHeartbeat(
      String accountId, String delegateId, DelegateConnectionHeartbeat heartbeat, ConnectionMode mode);

  DelegateProfileParams checkForProfile(String accountId, String delegateId, String profileId, long lastUpdatedAt);

  void saveProfileResult(String accountId, String delegateId, boolean error, FileBucket fileBucket,
      InputStream uploadedInputStream, FormDataContentDisposition fileDetail);

  String getProfileResult(String accountId, String delegateId);

  @ValidationGroups(Create.class) String queueTask(@Valid DelegateTask task);

  void scheduleSyncTask(DelegateTask task);

  <T extends DelegateResponseData> T executeTask(DelegateTask task) throws InterruptedException;

  String obtainDelegateName(Delegate delegate);

  String obtainDelegateName(String accountId, String delegateId, boolean forceRefresh);

  List<String> obtainDelegateIdsUsingName(String accountId, String delegateName);

  boolean filter(String accountId, String delegateId);

  Delegate updateHeartbeatForDelegateWithPollingEnabled(Delegate delegate);

  Delegate handleEcsDelegateRequest(Delegate delegate);

  File downloadDelegateValuesYamlFile(String managerHost, String verificationUrl, String accountId, String delegateName,
      String delegateProfile, String tokenName) throws IOException;

  List<Integer> getCountOfDelegatesForAccounts(List<String> collect);

  boolean validateThatDelegateNameIsUnique(String accountId, String delegateName);

  void delegateDisconnected(String accountId, String delegateId, String delegateConnectionId);

  void deleteAllDelegatesExceptOne(String accountId, long shutdownInterval);

  CEDelegateStatus validateCEDelegate(String accountId, String delegateName);

  List<DelegateSizeDetails> fetchAvailableSizes();

  List<String> getConnectedDelegates(String accountId, List<String> delegateIds);

  List<DelegateInitializationDetails> obtainDelegateInitializationDetails(String accountID, List<String> delegateIds);

  DelegateGroup upsertDelegateGroup(String name, String accountId, DelegateSetupDetails delegateSetupDetails);

  boolean sampleDelegateExists(String accountId);

  List<Delegate> getNonDeletedDelegatesForAccount(String accountId);

  boolean checkDelegateConnected(String accountId, String delegateId);

  void updateLastExpiredEventHeartbeatTime(long lastExpiredEventHeartbeatTime, String delegateId, String accountId);

  DelegateTask abortTask(String accountId, String delegateTaskId);

  String expireTask(String accountId, String delegateTaskId);

  DelegateSizeDetails fetchDefaultDockerDelegateSize();

  void validateDelegateSetupDetails(String accountId, DelegateSetupDetails delegateSetupDetails, String delegateType);

  File downloadNgDocker(String managerHost, String verificationServiceUrl, String accountId,
      DelegateSetupDetails delegateSetupDetails) throws IOException;

  String createDelegateGroup(String accountId, DelegateSetupDetails delegateSetupDetails);

  DelegateSetupDetails validateKubernetesYamlNg(String accountId, DelegateSetupDetails delegateSetupDetails);

  long getCountOfRegisteredDelegates(String accountId);

  long getCountOfConnectedDelegates(String accountId);

  DelegateDTO listDelegateTags(String accountId, String delegateId);

  DelegateDTO addDelegateTags(String accountId, String delegateId, DelegateTags delegateTags);

  DelegateDTO updateDelegateTags(String accountId, String delegateId, DelegateTags delegateTags);

  DelegateDTO deleteDelegateTags(String accountId, String delegateId);

  DelegateApprovalResponse approveDelegatesUsingProfile(
      String accountId, String delegateProfileId, DelegateApproval action) throws InvalidRequestException;

  DelegateApprovalResponse approveDelegatesUsingToken(
      String accountId, String delegateTokenName, DelegateApproval action) throws InvalidRequestException;

  void checkUniquenessOfDelegateName(String accountId, String delegateName, boolean isNg);
}
