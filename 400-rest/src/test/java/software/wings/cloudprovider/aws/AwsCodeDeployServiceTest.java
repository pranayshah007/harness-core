/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.cloudprovider.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANUBHAW;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.CodeDeployDeploymentInfo;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.codedeploy.model.CreateDeploymentRequest;
import com.amazonaws.services.codedeploy.model.RevisionLocation;
import com.amazonaws.services.codedeploy.model.S3Location;
import com.google.inject.Inject;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Created by anubhaw on 6/22/17.
 */
@Slf4j
@OwnedBy(CDP)
public class AwsCodeDeployServiceTest extends WingsBaseTest {
  @InjectMocks @Inject private AwsCodeDeployService awsCodeDeployService;
  @Inject private ScmSecret scmSecret;
  @Mock private AwsHelperService awsHelperService;

  String PUBLIC_DNS_NAME = "publicDnsName";
  SettingAttribute cloudProvider;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    cloudProvider =
        SettingAttribute.Builder.aSettingAttribute()
            .withValue(AwsConfig.builder()
                           .accessKey(scmSecret.decryptToCharArray(new SecretName("aws_playground_access_key")))
                           .secretKey(scmSecret.decryptToCharArray(new SecretName("aws_playground_secret_key")))
                           .build())
            .build();
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldListApplication() {
    awsCodeDeployService.listApplications(Regions.US_EAST_1.getName(), cloudProvider, Collections.emptyList())
        .forEach(application -> { log.info(application.toString()); });

    awsCodeDeployService
        .listDeploymentGroup(Regions.US_EAST_1.getName(), "todolistwar", cloudProvider, Collections.emptyList())
        .forEach(dg -> { log.info(dg.toString()); });

    awsCodeDeployService
        .listDeploymentConfiguration(Regions.US_EAST_1.getName(), cloudProvider, Collections.emptyList())
        .forEach(dc -> { log.info(dc.toString()); });

    CreateDeploymentRequest createDeploymentRequest =
        new CreateDeploymentRequest()
            .withApplicationName("todolistwar")
            .withDeploymentGroupName("todolistwarDG")
            .withDeploymentConfigName("CodeDeployDefault.OneAtATime")
            .withRevision(new RevisionLocation().withRevisionType("S3").withS3Location(
                new S3Location()
                    .withBucket("harnessapps")
                    .withBundleType("zip")
                    .withKey("todolist_war/19/codedeploysample.zip")));
    CodeDeployDeploymentInfo codeDeployDeploymentInfo =
        awsCodeDeployService.deployApplication(Regions.US_EAST_1.getName(), cloudProvider, Collections.emptyList(),
            createDeploymentRequest, new ExecutionLogCallback(), 10);
    log.info(codeDeployDeploymentInfo.toString());
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldListApplicationRevisions() {
    log.info(awsCodeDeployService
                 .getApplicationRevisionList(Regions.US_EAST_1.getName(), "todolistwar", "todolistwarDG", cloudProvider,
                     Collections.emptyList())
                 .toString());
  }
}
