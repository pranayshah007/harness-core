/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;

import software.wings.graphql.datafetcher.AbstractObjectDataFetcher;
import software.wings.graphql.schema.query.QLArtifactQueryParameters;
import software.wings.graphql.schema.type.artifact.QLArtifact;
import software.wings.graphql.schema.type.artifact.QLArtifact.QLArtifactBuilder;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;

@OwnedBy(CDC)
public class ArtifactDataFetcher extends AbstractObjectDataFetcher<QLArtifact, QLArtifactQueryParameters> {
  @Inject HPersistence persistence;

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLArtifact fetch(QLArtifactQueryParameters parameters, String accountId) {
    Artifact artifact = persistence.get(Artifact.class, parameters.getArtifactId());
    if (artifact == null) {
      /**
       * Discussed this with Srinivas, most of the times it is possible that artifact may
       * not be present, so returning null in those cases and not throwing an exception
       */
      return null;
    }

    if (!artifact.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("Artifact does not exist", WingsException.USER);
    }

    QLArtifactBuilder qlArtifactBuilder = QLArtifact.builder();
    ArtifactController.populateArtifact(artifact, qlArtifactBuilder);
    return qlArtifactBuilder.build();
  }
}
