/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgLaunchTemplate;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.manifest.request.ManifestRequest;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import com.amazonaws.services.ec2.model.LaunchTemplate;
import com.amazonaws.services.ec2.model.LaunchTemplateVersion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(CDP)
public class AsgLaunchTemplateManifestHandler extends AsgManifestHandler<CreateLaunchTemplateRequest> {
  public interface OverrideProperties {
    String amiImageId = "amiImageId";
  }

  public AsgLaunchTemplateManifestHandler(AsgSdkManager asgSdkManager, ManifestRequest manifestRequest) {
    super(asgSdkManager, manifestRequest);
  }

  @Override
  public Class<CreateLaunchTemplateRequest> getManifestContentUnmarshallClass() {
    return CreateLaunchTemplateRequest.class;
  }

  public void applyOverrideProperties(CreateLaunchTemplateRequest request, Map<String, Object> overrideProperties) {
    overrideProperties.entrySet().stream().forEach(entry -> {
      switch (entry.getKey()) {
        case AsgLaunchTemplateManifestHandler.OverrideProperties.amiImageId:
          request.getLaunchTemplateData().setImageId(entry.getValue().toString());
          break;
        default:
          // do nothing
      }
    });
  }

  @Override
  public AsgManifestHandlerChainState upsert(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    if (chainState.getLaunchTemplateVersion() == null) {
      AsgLaunchTemplateManifestRequest asgLaunchTemplateManifestRequest =
          (AsgLaunchTemplateManifestRequest) manifestRequest;

      List<CreateLaunchTemplateRequest> manifests =
          manifestRequest.getManifests().stream().map(this::parseContentToManifest).collect(Collectors.toList());
      String asgName = chainState.getAsgName();
      CreateLaunchTemplateRequest createLaunchTemplateRequest = manifests.get(0);
      // launch template should always have same name as ASG name
      createLaunchTemplateRequest.setLaunchTemplateName(asgName);

      if (isNotEmpty(asgLaunchTemplateManifestRequest.getOverrideProperties())) {
        applyOverrideProperties(createLaunchTemplateRequest, asgLaunchTemplateManifestRequest.getOverrideProperties());
      }

      // encode user data script in base64
      if (isNotEmpty(createLaunchTemplateRequest.getLaunchTemplateData().getUserData())) {
        createLaunchTemplateRequest.getLaunchTemplateData().setUserData(
            encodeBase64(createLaunchTemplateRequest.getLaunchTemplateData().getUserData()));
      }

      LaunchTemplate launchTemplate = asgSdkManager.getLaunchTemplate(asgName);
      if (launchTemplate != null) {
        LaunchTemplateVersion launchTemplateVersion = asgSdkManager.createLaunchTemplateVersion(
            launchTemplate, createLaunchTemplateRequest.getLaunchTemplateData());
        chainState.setLaunchTemplateVersion(launchTemplateVersion.getVersionNumber().toString());
      } else {
        launchTemplate = asgSdkManager.createLaunchTemplate(asgName, createLaunchTemplateRequest);
        chainState.setLaunchTemplateVersion(launchTemplate.getLatestVersionNumber().toString());
      }
    }
    // currently assuming that during rollback - the launch template would always be available already and thus the
    // above if condition will not be executed

    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState delete(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState getManifestTypeContent(
      AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    if (chainState.getAutoScalingGroup() == null) {
      AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(chainState.getAsgName());
      chainState.setAutoScalingGroup(autoScalingGroup);
    }

    AutoScalingGroup autoScalingGroup = chainState.getAutoScalingGroup();
    if (autoScalingGroup != null) {
      String launchTemplateVersion = autoScalingGroup.getLaunchTemplate().getVersion();

      List<String> launchTemplateVersionList = new ArrayList<>();
      launchTemplateVersionList.add(launchTemplateVersion);

      Map<String, List<String>> asgManifestsDataForRollback = chainState.getAsgManifestsDataForRollback();
      if (asgManifestsDataForRollback == null) {
        Map<String, List<String>> asgManifestsDataForRollback2 = new HashMap<>();
        asgManifestsDataForRollback2.put(AsgLaunchTemplate, launchTemplateVersionList);
        chainState.setAsgManifestsDataForRollback(asgManifestsDataForRollback2);
      } else {
        asgManifestsDataForRollback.put(AsgLaunchTemplate, launchTemplateVersionList);
        chainState.setAsgManifestsDataForRollback(asgManifestsDataForRollback);
      }
    }
    return chainState;
  }

  @Override
  protected void validateManifest(CreateLaunchTemplateRequest manifest) {
    if (manifest.getLaunchTemplateData() == null) {
      throw new InvalidRequestException("`LaunchTemplateData` is a required property for AsgLaunchTemplate manifest");
    }
  }
}
