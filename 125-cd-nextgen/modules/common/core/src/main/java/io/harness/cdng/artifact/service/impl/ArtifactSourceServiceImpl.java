/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.service.impl;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource;
import io.harness.cdng.artifact.service.ArtifactSourceService;
import io.harness.repositories.artifact.ArtifactRepository;
import io.harness.repositories.artifact.ArtifactSourceDao;
import io.harness.validation.Create;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
@ValidateOnExecution
public class ArtifactSourceServiceImpl implements ArtifactSourceService {
  @Inject private ArtifactSourceDao artifactSourceDao;
  @Inject private ArtifactRepository artifactRepository;

  @Override
  @ValidationGroups(Create.class)
  public ArtifactSource create(ArtifactSource artifactSource) {
    return create(artifactSource, true);
  }

  @Override
  @ValidationGroups(Create.class)
  public ArtifactSource create(ArtifactSource artifactSource, boolean validate) {
    // TODO(archit): use usageRestrictions later.
    // TODO(archit): Validate artifact source.
    return artifactSourceDao.create(artifactSource);
  }

  @Override
  @ValidationGroups(Create.class)
  public ArtifactSource saveOrGetArtifactStream(ArtifactSource artifactSource) {
    ArtifactSource existingArtifactSource =
        getArtifactStreamByHash(artifactSource.getAccountId(), artifactSource.getUniqueHash());
    if (existingArtifactSource == null) {
      return create(artifactSource);
    }
    return existingArtifactSource;
  }

  @Override
  public ArtifactSource get(String accountId, String uuid) {
    return artifactSourceDao.get(accountId, uuid);
  }

  @Override
  public ArtifactSource getArtifactStreamByHash(String accountId, String uniqueHash) {
    return artifactSourceDao.getArtifactStreamByHash(accountId, uniqueHash);
    // return artifactSourceDao.
  }
}
