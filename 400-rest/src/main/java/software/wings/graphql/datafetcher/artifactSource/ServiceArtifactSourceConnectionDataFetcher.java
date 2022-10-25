/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifactSource;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.graphql.datafetcher.artifactSource.ArtifactSourceController.populateArtifactSource;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.graphql.datafetcher.AbstractArrayDataFetcher;
import software.wings.graphql.schema.query.QLArtifactSourceQueryParam;
import software.wings.graphql.schema.type.artifactSource.QLArtifactSource;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ServiceArtifactSourceConnectionDataFetcher
    extends AbstractArrayDataFetcher<QLArtifactSource, QLArtifactSourceQueryParam> {
  @Inject ArtifactStreamService artifactStreamService;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.SERVICE, action = PermissionAttribute.Action.READ)
  protected List<QLArtifactSource> fetch(QLArtifactSourceQueryParam parameters, String accountId) {
    try (AutoLogContext ignore0 = new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      String serviceId = parameters.getServiceId();
      String appId = parameters.getApplicationId();
      List<ArtifactStream> artifactStreams = artifactStreamService.getArtifactStreamsForService(appId, serviceId);
      List<QLArtifactSource> result = new ArrayList<>();
      if (isNotEmpty(artifactStreams)) {
        for (ArtifactStream artifactStream : artifactStreams) {
          if (!artifactStream.isArtifactStreamParameterized()) {
            result.add(populateArtifactSource(artifactStream));
          } else {
            List<String> params = artifactStreamService.getArtifactStreamParameters(artifactStream.getUuid());
            result.add(populateArtifactSource(artifactStream, params));
          }
        }
      }
      return result;
    }
  }

  @Override
  protected QLArtifactSource unusedReturnTypePassingDummyMethod() {
    return null;
  }
}
