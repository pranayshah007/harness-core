/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.service.intfc.ownership.OwnedByService;
import software.wings.utils.ArtifactType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * ArtifactStreamService.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface ArtifactStreamService extends OwnedByService {
  PageResponse<ArtifactStream> list(PageRequest<ArtifactStream> req);

  PageResponse<ArtifactStream> list(
      PageRequest<ArtifactStream> req, String accountId, boolean withArtifactCount, String artifactSearchString);

  PageResponse<ArtifactStream> list(PageRequest<ArtifactStream> req, String accountId, boolean withArtifactCount,
      String artifactSearchString, ArtifactType artifactType, int maxArtifacts);

  ArtifactStream get(String artifactStreamId);

  ArtifactStream getArtifactStreamByName(String appId, String serviceId, String artifactStreamName);

  ArtifactStream getArtifactStreamByName(String settingId, String artifactStreamName);

  @ValidationGroups(Create.class) ArtifactStream create(@Valid ArtifactStream artifactStream);

  @ValidationGroups(Create.class) ArtifactStream create(@Valid ArtifactStream artifactStream, boolean validate);

  @ValidationGroups(Update.class) ArtifactStream update(@Valid ArtifactStream artifactStream);

  @ValidationGroups(Update.class) ArtifactStream update(@Valid ArtifactStream artifactStream, boolean validate);

  @ValidationGroups(Update.class)
  ArtifactStream update(@Valid ArtifactStream artifactStream, boolean validate, boolean fromTemplate);

  boolean delete(@NotEmpty String appId, @NotEmpty String artifactStreamId);

  boolean delete(@NotEmpty String artifactStreamId, boolean syncFromGit);

  void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String artifactStreamId);

  Map<String, String> getSupportedBuildSourceTypes(String appId, String serviceId);

  boolean artifactStreamsExistForService(String appId, String serviceId);

  List<ArtifactStream> getArtifactStreamsForService(String appId, String serviceId);

  List<ArtifactStream> getArtifactStreamsForService(String appId, String serviceId, List<String> projections);

  Map<String, String> fetchArtifactSourceProperties(String accountId, String artifactStreamId);

  List<ArtifactStream> fetchArtifactStreamsForService(String appId, String serviceId);

  List<String> fetchArtifactStreamIdsForService(String appId, String serviceId);

  boolean updateFailedCronAttemptsAndLastIteration(
      String accountId, String artifactStreamId, int counter, boolean success);

  boolean updateCollectionStatus(String accountId, String artifactStreamId, String collectionStatus);

  List<ArtifactStream> listAllBySettingId(String settingId);

  List<ArtifactStream> listBySettingId(String settingId);

  List<ArtifactStream> listByIds(Collection<String> artifactStreamIds);

  List<ArtifactStreamSummary> listArtifactStreamSummary(String appId);

  ArtifactStream createWithBinding(String appId, ArtifactStream artifactStream, boolean validate);

  boolean deleteWithBinding(String appId, String artifactStreamId, boolean forceDelete, boolean syncFromGit);

  List<ArtifactStream> listBySettingId(String appId, String settingId);

  List<ArtifactStream> listByAppId(String appId);

  boolean pruneArtifactStream(ArtifactStream artifactStream);

  boolean attachPerpetualTaskId(ArtifactStream artifactStream, String perpetualTaskId);

  boolean detachPerpetualTaskId(String perpetualTaskId);

  List<String> getArtifactStreamParameters(String artifactStreamId);

  void deleteArtifacts(String accountId, ArtifactStream artifactStream);

  ArtifactStream fetchByArtifactSourceVariableValue(String appId, String variableValue);

  boolean deletePerpetualTaskByArtifactStream(String accountId, String artifactStreamId);

  boolean updateLastIterationFields(String accountId, String uuid, boolean success);

  ArtifactStream resetStoppedArtifactCollection(String appId, String artifactStreamId);

  void updateCollectionEnabled(ArtifactStream artifactStream, boolean collectionEnabled);

  Map<String, String> getArtifactStreamNames(@NotEmpty String appId, Set<String> artifactStreamIds);
}
