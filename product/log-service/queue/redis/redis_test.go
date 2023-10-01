// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"errors"
	"testing"

	"github.com/alicebob/miniredis/v2"
	"github.com/go-redis/redismock/v9"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"

	"github.com/harness/harness-core/product/log-service/entity"
)

func TestRedis_Consume_Error(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	group := "group"

	cliMock, mock := redismock.NewClientMock()
	mock.ExpectXReadGroup(&redis.XReadGroupArgs{
		Group:    group,
		Consumer: "consumer 1",
		Streams:  []string{stream},
		Count:    1,
		Block:    0,
		NoAck:    true,
	}).SetErr(errors.New("err"))

	rdb := &Redis{
		Client: cliMock,
	}

	consume, err := rdb.Consume(ctx, stream, group, "consumer 1")

	assert.NotNil(t, err)
	assert.Nil(t, consume)
}

func TestRedis_Consume(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	key := "key"
	zipKey := "zipkey"
	group := "group"
	values := []string{"file1", "file2"}

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}
	rdb.Create(ctx, stream, group)

	err = rdb.Produce(ctx, stream, key, zipKey, values)
	if err != nil {
		t.Error(err)
	}

	consume, err := rdb.Consume(ctx, stream, group, "consumer 1")

	assert.Nil(t, err)
	assert.Equal(t, mr.Exists(stream), true)
	assert.NotNil(t, consume)
}

func TestRedis_Create(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	group := "group"

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}

	err = rdb.Create(ctx, stream, group)

	assert.Nil(t, err)
	assert.Equal(t, mr.Exists(stream), true)

	// trying to create existent stream and return an error
	err = rdb.Create(ctx, stream, group)
	assert.NotNil(t, err)
}

func TestRedis_Create_Error(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	group := "group"

	cliMock, mock := redismock.NewClientMock()
	mock.ExpectXGroupCreateMkStream(stream, group, "0").SetErr(errors.New("err"))

	rdb := &Redis{
		Client: cliMock,
	}

	err := rdb.Create(ctx, stream, group)
	assert.NotNil(t, err)
}

func TestRedis_Produce_Error(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	key := "key"
	zipKey := "zipkey"
	values := []string{"file1", "file2"}
	args := &redis.XAddArgs{
		Stream: stream,
		Values: map[string]interface{}{"prefix": entity.EventQueue{key, zipKey, values}},
	}

	cliMock, mock := redismock.NewClientMock()
	mock.ExpectXAdd(args).SetErr(errors.New("err"))

	rdb := &Redis{
		Client: cliMock,
	}

	err := rdb.Produce(ctx, stream, key, zipKey, values)
	assert.NotNil(t, err)
}

func TestRedis_Produce(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	key := "key"
	zipKey := "zipkey"
	group := "group"
	values := []string{"file1", "file2"}

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}
	rdb.Create(ctx, stream, group)

	err = rdb.Produce(ctx, stream, key, zipKey, values)
	assert.Nil(t, err)
	assert.Equal(t, mr.Exists(stream), true)
}

func TestRedis_Produce_WhenStreamNotExists(t *testing.T) {
	ctx := context.Background()
	stream := "stream"
	key := "key"
	zipKey := "zipkey"
	values := []string{"file1", "file2"}

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli := redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}

	err = rdb.Produce(ctx, stream, key, zipKey, values)
	assert.Nil(t, err)
	assert.Equal(t, mr.Exists(stream), true)
}
