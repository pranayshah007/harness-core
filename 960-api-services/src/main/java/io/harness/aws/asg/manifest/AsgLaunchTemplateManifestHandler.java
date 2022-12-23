/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgSdkManager;

import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public class AsgLaunchTemplateManifestHandler extends AsgManifestHandler<CreateLaunchTemplateRequest> {
  public AsgLaunchTemplateManifestHandler(
      AsgSdkManager asgSdkManager, List<String> manifestContentList, Map<String, Object> overrideProperties) {
    super(asgSdkManager, manifestContentList, overrideProperties);
  }

  @Override
  public Class<CreateLaunchTemplateRequest> getManifestContentUnmarshallClass() {
    return CreateLaunchTemplateRequest.class;
  }

  @Override
  public void applyOverrideProperties(
      List<CreateLaunchTemplateRequest> manifests, Map<String, Object> overrideProperties) {}

  @Override
  public AsgManifestHandlerChainState upsert(
      AsgManifestHandlerChainState chainState, List<CreateLaunchTemplateRequest> manifests) {
    String asgName = chainState.getAsgName();
    CreateLaunchTemplateRequest createLaunchTemplateRequest = manifests.get(0);
    // launch template should always have same name as ASG name
    createLaunchTemplateRequest.setLaunchTemplateName(asgName);

    LaunchTemplate launchTemplate = asgSdkManager.getLaunchTemplate(asgName);
    if (launchTemplate != null) {
      LaunchTemplateVersion launchTemplateVersion = asgSdkManager.createLaunchTemplateVersion(
          launchTemplate, createLaunchTemplateRequest.getLaunchTemplateData());
      chainState.setLaunchTemplateVersion(launchTemplateVersion.getVersionNumber().toString());
    } else {
      launchTemplate = asgSdkManager.createLaunchTemplate(asgName, createLaunchTemplateRequest);
      chainState.setLaunchTemplateVersion(launchTemplate.getLatestVersionNumber().toString());
    }

    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState delete(
      AsgManifestHandlerChainState chainState, List<CreateLaunchTemplateRequest> manifests) {
    return chainState;
  }
}
