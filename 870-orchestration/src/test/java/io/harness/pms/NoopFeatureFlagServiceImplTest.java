package io.harness.pms;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class NoopFeatureFlagServiceImplTest {
  @InjectMocks NoopFeatureFlagServiceImpl noopFeatureFlagService;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  String accountId = "accountId";
  String featureName = "feature";

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void isEnabledFeatFlagServiceImpl() throws ExecutionException {
    Boolean flag = noopFeatureFlagService.isEnabled(accountId, featureName);
    assertThat(flag).isEqualTo(false);

    Boolean flag2 = noopFeatureFlagService.isEnabled(accountId, FeatureName.NG_GITOPS);
    assertThat(flag2).isEqualTo(false);
  }
}
