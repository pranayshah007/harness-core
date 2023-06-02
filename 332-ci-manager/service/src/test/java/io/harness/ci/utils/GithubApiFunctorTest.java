/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.ci.utils.GithubApiFunctorTestHelper.RESOLVED_EXPRESSION_REGEX;
import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.exception.FunctorException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.rule.Owner;

import java.util.regex.Pattern;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class GithubApiFunctorTest extends CategoryTest {
  @Mock private ConnectorUtils connectorUtils;
  private static NGAccess ngAccess;

  private static Pattern pattern;

  @BeforeClass
  public static void beforeClass() throws Exception {
    ngAccess = BaseNGAccess.builder()
                   .accountIdentifier("AccountId")
                   .orgIdentifier("OrgIdentifier")
                   .projectIdentifier("ProjectIdentifier")
                   .build();
    pattern = Pattern.compile(RESOLVED_EXPRESSION_REGEX);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldTestFunctorEvaluation() {
    GithubApiFunctor githubApiFunctor = GithubApiFunctor.builder()
                                            .connectorUtils(connectorUtils)
                                            .githubApiFunctorConfig(GithubApiFunctor.Config.builder()
                                                                        .codeBaseConnectorRef("codeBaseConnectorRef")
                                                                        .fetchConnector(true)
                                                                        .build())
                                            .ngAccess(ngAccess)
                                            .build();

    assertThat(githubApiFunctor.token()).asString().matches(pattern);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenCodebaseIsNull() {
    GithubApiFunctor githubApiFunctor =
        GithubApiFunctor.builder()
            .connectorUtils(connectorUtils)
            .githubApiFunctorConfig(GithubApiFunctor.Config.builder().fetchConnector(true).build())
            .ngAccess(ngAccess)
            .build();

    assertThatThrownBy(githubApiFunctor::token).isInstanceOf(FunctorException.class);
    assertThatThrownBy(() -> githubApiFunctor.token("")).isInstanceOf(FunctorException.class);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenConnectorUtilsIsNull() {
    GithubApiFunctor githubApiFunctor =
        GithubApiFunctor.builder()
            .githubApiFunctorConfig(GithubApiFunctor.Config.builder().fetchConnector(true).build())
            .ngAccess(ngAccess)
            .build();

    assertThatThrownBy(githubApiFunctor::token).isInstanceOf(FunctorException.class);
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldThrowExceptionWhenConnectorUtilsThrowsException() {
    GithubApiFunctor githubApiFunctor = GithubApiFunctor.builder()
                                            .githubApiFunctorConfig(GithubApiFunctor.Config.builder()
                                                                        .fetchConnector(true)
                                                                        .codeBaseConnectorRef("codeBaseConnectorRef")
                                                                        .build())
                                            .connectorUtils(connectorUtils)
                                            .ngAccess(ngAccess)
                                            .build();

    when(connectorUtils.getConnectorDetails(ngAccess, "codeBaseConnectorRef", true)).thenThrow(new RuntimeException());

    when(connectorUtils.getConnectorDetails(ngAccess, "account.anotherConnector", true))
        .thenThrow(new RuntimeException());
    assertThatThrownBy(githubApiFunctor::token).isInstanceOf(FunctorException.class);
    assertThatThrownBy(() -> githubApiFunctor.token("account.anotherConnector")).isInstanceOf(FunctorException.class);
  }
}
