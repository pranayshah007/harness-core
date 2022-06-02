package io.harness.delegate.beans.connector.scm.utils;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class ScmConnectorHelperTest extends CategoryTest {
  String filePath = "filePath";
  String branch = "branch";
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifConnectorIsRepoType() {
    try {
      ScmConnectorHelper.validateGetFileUrlParams(GitConnectionType.ACCOUNT, branch, filePath);
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifBranchNameIsNull() {
    try {
      ScmConnectorHelper.validateGetFileUrlParams(GitConnectionType.REPO, null, filePath);
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifFilePathIsEmpty() {
    try {
      ScmConnectorHelper.validateGetFileUrlParams(GitConnectionType.REPO, branch, "");
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }
}
