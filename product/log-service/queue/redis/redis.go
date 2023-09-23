// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/entity"
	"github.com/harness/harness-core/product/log-service/queue"
	"github.com/harness/harness-core/product/log-service/store"
)

type Redis struct {
	Client redis.Cmdable
	queue  queue.Queue
	store  store.Store
	cache  cache.Cache
}

const (
	usePrefixParam = "prefix"
)

func NewWithClient(cmdable *redis.Cmdable) *Redis {
	return &Redis{Client: *cmdable}
}

func (r Redis) Produce(ctx context.Context, stream, key, zipKey string, values []string) error {
	_, err := r.Client.XAdd(ctx, &redis.XAddArgs{
		Stream: stream,
		Values: map[string]interface{}{usePrefixParam: entity.EventQueue{key, zipKey, values}},
	}).Result()
	if err != nil {
		return err
	}
	_, err = r.Client.Expire(ctx, key, time.Minute*10).Result()

	if err != nil {
		return err
	}

	return nil
}

func (r Redis) Consume(ctx context.Context, stream string, consumerGroup string, name string) (map[string]interface{}, error) {
	streams, err := r.Client.XReadGroup(ctx, &redis.XReadGroupArgs{
		Streams:  []string{stream, ">"},
		Group:    consumerGroup,
		Consumer: name,
		Count:    1,
		Block:    5 * time.Second,
		NoAck:    false,
	}).Result()

	if err != nil {
		if strings.Contains(err.Error(), "redis: nil") {
			return nil, err
		}
		//re-create stream and try to consume again
		fmt.Println("re-creating stream due to error", err)
		createErr := r.Create(ctx, stream, consumerGroup)
		if createErr == nil || strings.Contains(createErr.Error(), "already exists") {
			streams, err = r.Client.XReadGroup(ctx, &redis.XReadGroupArgs{
				Streams:  []string{stream, ">"},
				Group:    consumerGroup,
				Consumer: name,
				Count:    1,
				Block:    5 * time.Second,
				NoAck:    false,
			}).Result()
			if err != nil {
				return nil, fmt.Errorf("error while Re-Reading from stream: %w", err)
			}
		} else {
			return nil, fmt.Errorf("error while Re-creating stream: %w", createErr)
		}
	}

	if len(streams) == 0 {
		return nil, nil
	}

	if len(streams[0].Messages) == 0 {
		return nil, fmt.Errorf("no messages in the first slice") // No messages in the first slice
	}

	// after consume delete message from the stream
	r.Client.XDel(context.Background(), stream, streams[0].Messages[0].ID)
	return streams[0].Messages[0].Values, nil
}

func (r Redis) Create(ctx context.Context, stream, group string) error {
	_, err := r.Client.XGroupCreateMkStream(ctx, stream, group, "0").Result()
	if err != nil {
		return err
	}
	return nil
}
