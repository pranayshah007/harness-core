/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by rishi on 12/22/16.
 */
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public enum DeploymentType {
  SSH("Secure Shell (SSH)"),
  AWS_CODEDEPLOY("AWS CodeDeploy"),
  ECS("Amazon EC2 Container Services (ECS)"),
  SPOTINST("SPOTINST"),
  KUBERNETES("Kubernetes"),
  HELM("Helm"),
  AWS_LAMBDA("AWS Lambda"),
  AMI("AMI"),
  WINRM("Windows Remote Management (WinRM)"),
  PCF("Tanzu Application Services"),
  AZURE_VMSS("Azure Virtual Machine Image"),
  AZURE_WEBAPP("Azure Web App"),
  CUSTOM("Custom");

  private String displayName;

  DeploymentType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public static final ImmutableMap<DeploymentType, List<ArtifactType>> supportedArtifactTypes =
      ImmutableMap.<DeploymentType, List<ArtifactType>>builder()
          .put(SSH,
              Arrays.asList(ArtifactType.DOCKER, ArtifactType.JAR, ArtifactType.WAR, ArtifactType.RPM,
                  ArtifactType.OTHER, ArtifactType.TAR, ArtifactType.ZIP))
          .put(ECS, Collections.singletonList(ArtifactType.DOCKER))
          .put(KUBERNETES, Collections.singletonList(ArtifactType.DOCKER))
          .put(HELM, Collections.singletonList(ArtifactType.DOCKER))
          .put(AWS_CODEDEPLOY, Collections.singletonList(ArtifactType.AWS_CODEDEPLOY))
          .put(AWS_LAMBDA, Collections.singletonList(ArtifactType.AWS_LAMBDA))
          .put(AMI, Collections.singletonList(ArtifactType.AMI))
          .put(WINRM, Arrays.asList(ArtifactType.IIS, ArtifactType.IIS_APP, ArtifactType.IIS_VirtualDirectory))
          .put(AZURE_VMSS, Collections.singletonList(ArtifactType.AZURE_MACHINE_IMAGE))
          .put(AZURE_WEBAPP, Arrays.asList(ArtifactType.WAR, ArtifactType.ZIP, ArtifactType.NUGET, ArtifactType.DOCKER))
          .put(PCF, Collections.singletonList(ArtifactType.PCF))
          .put(CUSTOM,
              Arrays.asList(ArtifactType.DOCKER, ArtifactType.JAR, ArtifactType.WAR, ArtifactType.RPM,
                  ArtifactType.OTHER, ArtifactType.TAR, ArtifactType.ZIP))
          .build();
}
