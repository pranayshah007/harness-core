/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.ng.userprofile.entities.AwsCodeCommitSCM;
import io.harness.ng.userprofile.entities.AzureRepoSCM;
import io.harness.ng.userprofile.entities.BitbucketSCM;
import io.harness.ng.userprofile.entities.GithubSCM;
import io.harness.ng.userprofile.entities.GitlabSCM;
import io.harness.ng.userprofile.entities.SourceCodeManager;

import java.util.Set;

@OwnedBy(PL)
public class NgUserProfileMorphiaRegistrars implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(SourceCodeManager.class);
    set.add(BitbucketSCM.class);
    set.add(GithubSCM.class);
    set.add(GitlabSCM.class);
    set.add(AwsCodeCommitSCM.class);
    set.add(AzureRepoSCM.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}
