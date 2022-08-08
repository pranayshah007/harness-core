/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import io.harness.logging.CommandExecutionStatus;

import software.wings.api.DeploymentType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by anubhaw on 12/20/17.
 */

@JsonTypeName("AWS_AMI")
public class AmiCommandUnit extends AbstractCommandUnit {
  public AmiCommandUnit() {
    super(CommandUnitType.AWS_AMI);
    setArtifactNeeded(true);
    setDeploymentType(DeploymentType.AMI.name());
  }

  @Override
  public CommandExecutionStatus execute(CommandExecutionContext context) {
    return null;
  }
  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("AWS_AMI")
  public static class Yaml extends AbstractCommandUnit.Yaml {
    public Yaml() {
      super(CommandUnitType.AWS_AMI.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType) {
      super(name, CommandUnitType.AWS_AMI.name(), deploymentType);
    }
  }
}
