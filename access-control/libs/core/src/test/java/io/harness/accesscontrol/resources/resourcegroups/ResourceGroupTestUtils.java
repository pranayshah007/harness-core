/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.resources.resourcegroups;

import static io.harness.accesscontrol.common.AccessControlTestUtils.getRandomString;

import io.harness.accesscontrol.resources.resourcegroups.ResourceGroup.ResourceGroupBuilder;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.Sets;
import javax.validation.executable.ValidateOnExecution;
import lombok.experimental.UtilityClass;
import org.hibernate.validator.constraints.NotEmpty;

@UtilityClass
@ValidateOnExecution
@OwnedBy(HarnessTeam.PL)
public class ResourceGroupTestUtils {
  private static ResourceGroupBuilder fetchBuilder(@NotEmpty String scopeIdentifier) {
    return ResourceGroup.builder().identifier(getRandomString(20)).scopeIdentifier(scopeIdentifier);
  }

  public static ResourceGroup buildResourceGroup(@NotEmpty String scopeIdentifier) {
    return fetchBuilder(scopeIdentifier)
        .resourceSelectors(Sets.newHashSet(getRandomString(20), getRandomString(20)))
        .build();
  }
}
