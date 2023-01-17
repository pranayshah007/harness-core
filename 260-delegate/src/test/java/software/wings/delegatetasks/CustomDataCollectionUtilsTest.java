/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.wings.delegatetasks.utils.CustomDataCollectionUtils;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class CustomDataCollectionUtilsTest extends WingsBaseTest {
  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMaskedString_whenNoMatchFound() {
    String matchPattern = "apiKey=(.*)";
    String stringToMask = "stringWithoutMatchPattern";

    String maskedString = CustomDataCollectionUtils.getMaskedString(stringToMask, matchPattern, new ArrayList<>());
    assertThat(maskedString).isEqualTo(stringToMask);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMaskedString_whenExactMatchFound() {
    String matchPattern = "apiKey=(.*)&appKey=(.*)";
    String stringToMask = "stringWithoutMatchPattern&apiKey=encryptedApiKey&appKey=encryptedAppKey";
    List<String> stringsToReplace = Arrays.asList("<apiKey>", "<appKey>");
    String expectedMaskedString = "stringWithoutMatchPattern&apiKey=<apiKey>&appKey=<appKey>";

    String maskedString = CustomDataCollectionUtils.getMaskedString(stringToMask, matchPattern, stringsToReplace);
    assertThat(maskedString).isEqualTo(expectedMaskedString);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMaskedString_whenStringsToReplaceAreMore() {
    String matchPattern = "apiKey=(.*)&appKey=(.*)";
    String stringToMask = "stringWithoutMatchPattern&apiKey=encryptedApiKey&appKey=encryptedAppKey";
    List<String> stringsToReplace = Arrays.asList("<apiKey>", "<appKey>", "<extraString>");
    String expectedMaskedString = "stringWithoutMatchPattern&apiKey=<apiKey>&appKey=<appKey>";

    String maskedString = CustomDataCollectionUtils.getMaskedString(stringToMask, matchPattern, stringsToReplace);
    assertThat(maskedString).isEqualTo(expectedMaskedString);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMaskedString_whenStringsToReplaceAreLess() {
    String matchPattern = "apiKey=(.*)&appKey=(.*)";
    String stringToMask = "stringWithoutMatchPattern&apiKey=encryptedApiKey&appKey=encryptedAppKey";
    List<String> stringsToReplace = Collections.singletonList("<appKey>");
    String expectedMaskedString = "stringWithoutMatchPattern&apiKey=<appKey>&appKey=encryptedAppKey";

    String maskedString = CustomDataCollectionUtils.getMaskedString(stringToMask, matchPattern, stringsToReplace);
    assertThat(maskedString).isEqualTo(expectedMaskedString);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testResolveField_emptyString() {
    String string = "";
    String returnValue = CustomDataCollectionUtils.resolveField(string, "%", "test");
    assertThat(returnValue).isEqualTo(string);

    string = null;
    returnValue = CustomDataCollectionUtils.resolveField(string, "%", "test");
    assertThat(returnValue).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testResolveField_nonEmptyString() {
    String string = "test${replace}";
    String returnValue = CustomDataCollectionUtils.resolveField(string, "${replace}", "replace");
    assertThat(returnValue).isEqualTo("testreplace");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testResolveDollarReferences() {
    Map<String, String> replacements = new HashMap<>();
    replacements.put("start_time_seconds", "10");
    replacements.put("end_time_seconds", "20");
    replacements.put("host", "abc");
    assertThat(CustomDataCollectionUtils.resolveDollarReferences(
                   "v2/export?from=${start_time_seconds}&to=${end_time_seconds}&hosts=${instance.name}&size=1000",
                   replacements))
        .isEqualTo("v2/export?from=10&to=20&hosts=${instance.name}&size=1000");
    assertThat(CustomDataCollectionUtils.resolveDollarReferences(
                   "v2/export?from=${start_time_seconds}&to=${end_time_seconds}", replacements))
        .isEqualTo("v2/export?from=10&to=20");
    assertThat(CustomDataCollectionUtils.resolveDollarReferences("v2/export?from=${start_time_seconds}", replacements))
        .isEqualTo("v2/export?from=10");
    assertThat(CustomDataCollectionUtils.resolveDollarReferences(
                   "v2/export?from=${start_time_seconds}&to=${end_time_seconds}&hosts=${instance.name}", replacements))
        .isEqualTo("v2/export?from=10&to=20&hosts=${instance.name}");
    assertThat(CustomDataCollectionUtils.resolveDollarReferences("v2/export?from=10", replacements))
        .isEqualTo("v2/export?from=10");
    assertThat(CustomDataCollectionUtils.resolveDollarReferences("v2/export?from=${}", replacements))
        .isEqualTo("v2/export?from=${}");
  }
}
