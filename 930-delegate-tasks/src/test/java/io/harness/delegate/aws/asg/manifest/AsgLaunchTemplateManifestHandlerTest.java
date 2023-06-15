/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.aws.asg.manifest;

import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import io.harness.CategoryTest;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.aws.asg.manifest.AsgLaunchTemplateManifestHandler;
import io.harness.aws.asg.manifest.AsgManifestHandlerChainState;
import io.harness.aws.asg.manifest.request.AsgLaunchTemplateManifestRequest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.manifest.handler.DefaultManifestContentParser;
import io.harness.rule.Owner;

import com.amazonaws.services.ec2.model.CreateLaunchTemplateRequest;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AsgLaunchTemplateManifestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock AsgSdkManager asgSdkManager;

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void upsertShouldFail() {
    AsgManifestHandlerChainState chainState = AsgManifestHandlerChainState.builder().build();

    AsgLaunchTemplateManifestRequest asgLaunchTemplateManifestRequest =
        new AsgLaunchTemplateManifestRequest(List.of("content"), Collections.emptyMap());
    AsgLaunchTemplateManifestHandler handler =
        new AsgLaunchTemplateManifestHandler(asgSdkManager, asgLaunchTemplateManifestRequest);

    try (MockedStatic<DefaultManifestContentParser> asgContentParserMockedStatic =
             mockStatic(DefaultManifestContentParser.class)) {
      asgContentParserMockedStatic.when(() -> DefaultManifestContentParser.parseJson(anyString(), any()))
          .thenReturn(new CreateLaunchTemplateRequest());

      assertThatThrownBy(() -> handler.upsert(chainState))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessage("`LaunchTemplateData` is a required property for AsgLaunchTemplate manifest");
    }
  }
}
