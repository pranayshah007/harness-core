package io.harness.ng.core.environment.mappers;

import static io.harness.rule.OwnerRule.ARCHIT;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.rule.Owner;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class EnvironmentFilterHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetCriteria() {
    String accountId = "ACCOUNT_ID";
    String orgIdentifier = "ORG_ID";
    String projectIdentifier = "PROJECT_ID";
    Criteria criteriaFromServiceFilter =
        EnvironmentFilterHelper.createCriteria(accountId, orgIdentifier, projectIdentifier);
    assertThat(criteriaFromServiceFilter).isNotNull();
    Document criteriaObject = criteriaFromServiceFilter.getCriteriaObject();
    assertThat(criteriaObject.get(EnvironmentKeys.accountId)).isEqualTo(accountId);
    assertThat(criteriaObject.get(EnvironmentKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(EnvironmentKeys.projectIdentifier)).isEqualTo(projectIdentifier);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGetUpdateOperations() {
    Environment environment = Environment.builder().build();
    Update updateOperations = EnvironmentFilterHelper.getUpdateOperations(environment);
    Set<String> stringSet = ((Document) updateOperations.getUpdateObject().get("$set")).keySet();
    PropertyDescriptor[] propertyDescriptors = BeanUtils.getPropertyDescriptors(Environment.class);
    Set<String> excludedFields = new HashSet<>(Arrays.asList(EnvironmentKeys.id, EnvironmentKeys.createdAt,
        EnvironmentKeys.lastModifiedAt, EnvironmentKeys.version, EnvironmentKeys.tags, "class"));

    for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
      boolean shouldExist =
          stringSet.contains(propertyDescriptor.getName()) || excludedFields.contains(propertyDescriptor.getName());
      assertThat(shouldExist).isTrue();
    }
  }
}
