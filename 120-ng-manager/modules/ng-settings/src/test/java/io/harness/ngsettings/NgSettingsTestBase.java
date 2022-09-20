package io.harness.ngsettings;

import io.harness.CategoryTest;
import io.harness.ngsettings.rule.NgSettingRule;
import io.harness.rule.LifecycleRule;

import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class NgSettingsTestBase extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public NgSettingRule ngSettingRule = new NgSettingRule(lifecycleRule.getClosingFactory());
}
