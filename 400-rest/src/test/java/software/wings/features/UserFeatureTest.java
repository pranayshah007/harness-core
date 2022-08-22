package software.wings.features;

import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UserFeatureTest extends WingsBaseTest {
  @Inject private UsersFeature usersFeature;

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUserLimitCommunity() {
    int limit = usersFeature.getMaxUsageAllowed("COMMUNITY");
    assertThat(limit).isEqualTo(5);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testUserLimitPaid() {
    int limit = usersFeature.getMaxUsageAllowed("PAID");
    assertThat(limit).isEqualTo(50000);
  }
}
