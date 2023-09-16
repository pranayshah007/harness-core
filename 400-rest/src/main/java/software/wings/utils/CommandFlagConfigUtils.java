/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.helm.HelmCommandFlagConstants.getHelmSubCommands;

import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.HelmVersion;

import software.wings.beans.helm.HelmCommandFlagConfig;
import software.wings.beans.helm.HelmCommandFlagConstants.HelmSubCommand;

import com.google.inject.Singleton;
import java.util.Set;
import lombok.experimental.UtilityClass;

@Singleton
@UtilityClass
public class CommandFlagConfigUtils {
  public void validateHelmCommandFlags(HelmCommandFlagConfig helmCommandFlag, HelmVersion helmVersion) {
    if (null != helmCommandFlag) {
      Set<HelmSubCommand> helmSubCommands = getHelmSubCommands(helmVersion);

      helmCommandFlag.getValueMap().forEach((k, v) -> {
        if (!helmSubCommands.contains(k)) {
          throw new InvalidRequestException(String.format("Invalid subCommand [%s] provided", k), USER);
        }
        if (isEmpty(v)) {
          throw new InvalidRequestException("Command flag provided is null", USER);
        }
      });
    }
  }
}
