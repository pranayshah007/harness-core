// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"github.com/go-redis/redismock/v8"
	"github.com/harness/harness-core/queue-service/hsqs/store/redis"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"os"
	"testing"
)

func TestNewHandler(t *testing.T) {
	rdb, _ := redismock.NewClientMock()
	l := zerolog.New(os.Stderr).With().Timestamp().Logger()
	s := &redis.Store{Client: rdb, Logger: &l}

	t.Run("TestConstructor", func(t *testing.T) {
		got := NewHandler(s)
		assert.Equal(t, s, got.s)
	})
}
