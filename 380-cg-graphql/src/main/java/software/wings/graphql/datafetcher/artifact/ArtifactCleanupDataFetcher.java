/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifact;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.workers.background.iterator.ArtifactCleanupHandler;

import software.wings.beans.User;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.artifact.ArtifactCleanUpPayload;
import software.wings.graphql.schema.mutation.artifact.ArtifactCleanupInput;
import software.wings.security.PermissionAttribute;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.AuthService;
import software.wings.utils.DelegateArtifactCollectionUtils;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ArtifactCleanupDataFetcher extends BaseMutatorDataFetcher<ArtifactCleanupInput, ArtifactCleanUpPayload> {
  private ArtifactCleanupHandler artifactCleanupHandler;
  private AuthService authService;
  private ArtifactStreamService artifactStreamService;

  @Inject
  public ArtifactCleanupDataFetcher(ArtifactStreamService artifactStreamService,
      ArtifactCleanupHandler artifactCleanupHandler, AuthService authService) {
    super(ArtifactCleanupInput.class, ArtifactCleanUpPayload.class);
    this.artifactCleanupHandler = artifactCleanupHandler;
    this.authService = authService;
    this.artifactStreamService = artifactStreamService;
  }

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.SERVICE)
  protected ArtifactCleanUpPayload mutateAndFetch(ArtifactCleanupInput parameter, MutationContext mutationContext) {
    ArtifactStream artifactStream = artifactStreamService.get(parameter.getArtifactStreamId());

    if (artifactStream == null) {
      return new ArtifactCleanUpPayload("Artifact stream not found for the id: " + parameter.getArtifactStreamId());
    }

    if (!DelegateArtifactCollectionUtils.supportsCleanup(artifactStream.getArtifactStreamType())) {
      return new ArtifactCleanUpPayload(
          "Clean up not supported for artifact Stream type: " + artifactStream.getArtifactStreamType());
    }

    String accountId = artifactStream.getAccountId();
    if (accountId == null) {
      accountId = artifactCleanupHandler.fetchAccountId(artifactStream);
    }

    final User user = UserThreadLocal.get();
    if (user != null) {
      authService.authorize(accountId, artifactStream.getAppId(), artifactStream.getServiceId(), user,
          asList(new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                     PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.READ),
              new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                  PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.CREATE),
              new PermissionAttribute(PermissionAttribute.ResourceType.SERVICE,
                  PermissionAttribute.PermissionType.SERVICE, PermissionAttribute.Action.UPDATE)),
          true);
    }

    artifactCleanupHandler.handleManually(artifactStream, accountId);

    return new ArtifactCleanUpPayload(
        "Cleanup successful for Artifact stream with id: " + parameter.getArtifactStreamId());
  }
}
