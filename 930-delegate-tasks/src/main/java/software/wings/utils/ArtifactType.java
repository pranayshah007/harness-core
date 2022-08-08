/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

/**
 * The Enum ArtifactType.
 */
@OwnedBy(CDC)
@TargetModule(_930_DELEGATE_TASKS)
public enum ArtifactType {
  /**
   * Jar artifact type.
   */
  JAR { private static final long serialVersionUID = 2932493038229748527L; },
  /**
   * War artifact type.
   */
  WAR { public static final long serialVersionUID = 2932493038229748527L; },
  /**
   * Tar artifact type.
   */
  TAR { private static final long serialVersionUID = 2932493038229748527L; },
  /**
   * Zip artifact type.
   */
  ZIP { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * NuGET artifact type.
   */
  NUGET { private static final long serialVersionUID = 2932493038229748527L; },
  /**
   * Docker artifact type.
   */
  DOCKER { private static final long serialVersionUID = 2932493038229748527L; },
  /**
   * RPM artifact type
   */
  RPM { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * The constant AWS_LAMBDA.
   */
  AWS_LAMBDA { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * The constant AWS_CODEDEPLOY.
   */
  AWS_CODEDEPLOY { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * The constant AWS_CODEDEPLOY.
   */
  PCF { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * The constant AWS_CODEDEPLOY.
   */
  AMI { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * The constant AZURE_MACHINE_IMAGE.
   */
  AZURE_MACHINE_IMAGE { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * The constant AZURE_WEBAPP.
   */
  AZURE_WEBAPP { private static final long serialVersionUID = 2932493038229748527L; },

  IIS { private static final long serialVersionUID = 2932493038229748527L; },

  /**
   * Other artifact type.
   */
  OTHER { private static final long serialVersionUID = 2932493038229748527L; },

  IIS_APP { private static final long serialVersionUID = 2932493038229748527L; },

  IIS_VirtualDirectory { private static final long serialVersionUID = 2932493038229748527L; };
}
