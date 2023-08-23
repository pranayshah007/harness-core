/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;

import static java.lang.String.format;

import io.harness.beans.yaml.extended.infrastrucutre.DockerInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.exception.ngexception.CIStageExecutionException;

import com.google.inject.Singleton;

@Singleton
public class DockerInitializeStepUtils {
  public static OSType getOS(Infrastructure infrastructure) {
    DockerInfraYaml dockerInfraYaml = (DockerInfraYaml) infrastructure;
    if (dockerInfraYaml.getSpec() == null) {
      throw new CIStageExecutionException("Docker infrastructure spec should not be empty");
    }

    if (dockerInfraYaml.getType() != Infrastructure.Type.DOCKER) {
      throw new CIStageExecutionException(format("Invalid DOCKER type: %s", dockerInfraYaml.getType()));
    }

    return resolveOSType(dockerInfraYaml.getSpec().getPlatform().getValue().getOs());
  }
}
