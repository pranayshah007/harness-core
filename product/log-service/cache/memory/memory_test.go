// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package memory

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestCacher_Create(t *testing.T) {
	c := New()
	err := c.Create(context.Background(), "1", struct{}{}, time.Hour)
	if err != nil {
		t.Error(err)
	}
	if len(c.cache) == 0 {
		t.Errorf("Want cache registered")
	}

	assert.Equal(t, 1, len(c.cache))
	assert.NoError(t, err)
}

func TestCacher_Delete(t *testing.T) {
	c := New()
	err := c.Delete(context.Background(), "1")
	assert.Equal(t, 0, len(c.cache))
	assert.NotNil(t, err)
	assert.Equal(t, err.Error(), "item not found")

	err = c.Create(context.Background(), "1", struct{}{}, time.Hour)
	if err != nil {
		t.Error(err)
	}
	if len(c.cache) == 0 {
		t.Errorf("Want cache registered")
	}
	err = c.Delete(context.Background(), "1")

	assert.Equal(t, 0, len(c.cache))
	assert.NoError(t, err)
}

func TestCacher_Exists(t *testing.T) {
	c := New()
	err := c.Create(context.Background(), "1", struct{}{}, time.Hour)
	if err != nil {
		t.Error(err)
	}
	if len(c.cache) == 0 {
		t.Errorf("Want cache registered")
	}
	assert.Equal(t, c.Exists(context.Background(), "1"), true)
	assert.Equal(t, c.Exists(context.Background(), "3"), false)
}

func TestCacher_Get(t *testing.T) {
	c := New()
	err := c.Create(context.Background(), "1", struct{}{}, time.Hour)
	if err != nil {
		t.Error(err)
	}
	if len(c.cache) == 0 {
		t.Errorf("Want cache registered")
	}
	one, err := c.Get(context.Background(), "1")
	assert.NotNil(t, one)
	assert.Nil(t, err)

	two, err := c.Get(context.Background(), "2")
	assert.Nil(t, two)
	assert.NotNil(t, err)
}
