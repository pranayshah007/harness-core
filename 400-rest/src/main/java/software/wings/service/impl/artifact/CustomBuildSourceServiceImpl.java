/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.SyncTaskContext;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.ServiceClassLocator;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildService;
import software.wings.service.intfc.artifact.CustomBuildSourceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
@Singleton
@ValidateOnExecution
@Slf4j
public class CustomBuildSourceServiceImpl implements CustomBuildSourceService {
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ServiceClassLocator serviceLocator;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public List<BuildDetails> getBuilds(String artifactStreamId) {
    log.info("Retrieving the builds for Custom Repository artifactStreamId {}", artifactStreamId);
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    notNullCheck("Artifact source does not exist", artifactStream, USER);
    return getBuildDetails((CustomArtifactStream) artifactStream);
  }

  private List<BuildDetails> getBuildDetails(CustomArtifactStream customArtifactStream) {
    // TODO: The rendering expression should be moved to delegate once the Framework is ready
    ArtifactStreamAttributes artifactStreamAttributes =
        artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream);

    // Defaulting to the 60 secs
    long timeout = isEmpty(artifactStreamAttributes.getCustomScriptTimeout())
        ? Long.parseLong(CustomArtifactStream.DEFAULT_SCRIPT_TIME_OUT)
        : Long.parseLong(artifactStreamAttributes.getCustomScriptTimeout());
    List<String> tags = customArtifactStream.getTags();
    if (isNotEmpty(tags)) {
      // To remove if any empty tags in case saved for custom artifact stream
      tags = tags.stream().filter(EmptyPredicate::isNotEmpty).distinct().collect(Collectors.toList());
    }

    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(artifactStreamAttributes.getAccountId())
                                          .appId(customArtifactStream.fetchAppId())
                                          .timeout(Duration.ofSeconds(timeout).toMillis())
                                          .tags(tags)
                                          .build();

    Class<? extends BuildService> buildServiceClass =
        serviceLocator.getBuildServiceClass(customArtifactStream.getArtifactStreamType());
    return delegateProxyFactory.getV2(buildServiceClass, syncTaskContext).getBuilds(artifactStreamAttributes);
  }

  @Override
  public boolean validateArtifactSource(ArtifactStream artifactStream) {
    log.info("Validating artifact source for Custom Repository artifactStreamId {}",
        artifactStream.fetchArtifactStreamAttributes(featureFlagService).getArtifactStreamId());
    notNullCheck("Artifact source does not exist", artifactStream, USER);

    CustomArtifactStream customArtifactStream = (CustomArtifactStream) artifactStream;

    // TODO: The rendering expression should be moved to delegate once the Framework is ready
    ArtifactStreamAttributes streamAttributes =
        artifactCollectionUtils.renderCustomArtifactScriptString(customArtifactStream);

    // Defaulting to the 60 secs
    long timeout = streamAttributes.getCustomScriptTimeout() == null
        ? 60
        : Long.parseLong(streamAttributes.getCustomScriptTimeout());
    List<String> tags = customArtifactStream.getTags();
    if (isNotEmpty(tags)) {
      // To remove if any empty tags in case saved for custom artifact stream
      tags = tags.stream().filter(EmptyPredicate::isNotEmpty).distinct().collect(Collectors.toList());
    }

    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(streamAttributes.getAccountId())
                                          .appId(artifactStream.fetchAppId())
                                          .timeout(Duration.ofSeconds(timeout).toMillis())
                                          .tags(tags)
                                          .build();

    Class<? extends BuildService> buildServiceClass =
        serviceLocator.getBuildServiceClass(customArtifactStream.getArtifactStreamType());
    return delegateProxyFactory.getV2(buildServiceClass, syncTaskContext).validateArtifactSource(streamAttributes);
  }
}
