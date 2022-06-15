/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.kryo.KryoPoolConfiguration;

import com.esotericsoftware.kryo.Kryo;
import java.util.Collections;
import java.util.Queue;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Fernando Dourado
 */
public class KryoSerializerTest extends CategoryTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateBoundedQueue() {
    final Queue<Kryo> queue = new KryoSerializer(Collections.emptySet())
                                  .createQueue(KryoPoolConfiguration.builder().queueCapacity(3).build());
    queue.offer(new Kryo());
    queue.offer(new Kryo());
    queue.offer(new Kryo());
    queue.offer(new Kryo());
    assertThat(queue.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateUnboundedQueue() {
    final Queue<Kryo> queue = new KryoSerializer(Collections.emptySet())
                                  .createQueue(KryoPoolConfiguration.builder().queueCapacity(0).build());
    queue.offer(new Kryo());
    queue.offer(new Kryo());
    queue.offer(new Kryo());
    queue.offer(new Kryo());
    assertThat(queue.size()).isEqualTo(4);
  }
}
