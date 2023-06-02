/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instance.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.instancesync.info.AwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsLambdaToServerInstanceInfoMapper;
import io.harness.delegate.task.aws.lambda.AwsLambda;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class AwsLambdaToServerInstanceInfoMapperTest extends CategoryTest {
  private final String FUNCTION = "fun";
  private final String VERSION = "version";
  private final String REGION = "us-east1";
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void toServerInstanceInfoListTest() {
    AwsLambda awsLambda = AwsLambda.builder().functionName(FUNCTION).runtime(RUN_TIME).version(VERSION).build();
    AwsLambdaServerInstanceInfo serverInstanceInfo =
        (AwsLambdaServerInstanceInfo) AwsLambdaToServerInstanceInfoMapper.toServerInstanceInfo(
            awsLambda, REGION, INFRA_KEY);
    assertThat(serverInstanceInfo.getFunctionName()).isEqualTo(FUNCTION);
    assertThat(serverInstanceInfo.getVersion()).isEqualTo(VERSION);
    assertThat(serverInstanceInfo.getRuntime()).isEqualTo(RUN_TIME);
    assertThat(serverInstanceInfo.getInfrastructureKey()).isEqualTo(INFRA_KEY);
  }
}
