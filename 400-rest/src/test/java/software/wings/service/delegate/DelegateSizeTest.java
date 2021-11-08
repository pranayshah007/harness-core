package software.wings.service.delegate;

import static io.harness.rule.OwnerRule.BOJAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;

import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployVariant;
import io.harness.delegate.beans.DelegateSizeDetails;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.DelegateServiceImpl;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DeployVariant.class})
@PowerMockIgnore({"javax.security.*", "javax.net.*", "javax.management.*", "javax.crypto.*"})
public class DelegateSizeTest extends WingsBaseTest {
  @Inject private DelegateServiceImpl delegateService;

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testFetchDefaultDelegateSize() {
    PowerMockito.mockStatic(DeployVariant.class);
    PowerMockito.when(DeployVariant.isCommunity(anyString())).thenReturn(false);

    DelegateSizeDetails delegateSizeDetails = delegateService.fetchDefaultDelegateSize();

    assertThat(delegateSizeDetails.getCpu()).isEqualTo(0.5);
    assertThat(delegateSizeDetails.getLabel()).isEqualTo("Default");
    assertThat(delegateSizeDetails.getRam()).isEqualTo(2048);
    assertThat(delegateSizeDetails.getReplicas()).isEqualTo(0);
    assertThat(delegateSizeDetails.getSize()).isNull();
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testFetchDefaultCommunityDelegateSize() {
    PowerMockito.mockStatic(DeployVariant.class);
    PowerMockito.when(DeployVariant.isCommunity(anyString())).thenReturn(true);

    DelegateSizeDetails delegateSizeDetails = delegateService.fetchDefaultDelegateSize();

    assertThat(delegateSizeDetails.getCpu()).isEqualTo(0.5);
    assertThat(delegateSizeDetails.getLabel()).isEqualTo("Default Community Size");
    assertThat(delegateSizeDetails.getRam()).isEqualTo(768);
    assertThat(delegateSizeDetails.getReplicas()).isEqualTo(0);
    assertThat(delegateSizeDetails.getSize()).isNull();
  }
}
