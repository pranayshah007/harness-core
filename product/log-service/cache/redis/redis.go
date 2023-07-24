// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"time"

	"github.com/redis/go-redis/v9"
)

type Redis struct {
	Client redis.Cmdable
}

func NewWithClient(cmdable *redis.Cmdable) *Redis {
	return &Redis{Client: *cmdable}
}

func New(endpoint, password string, useTLS, disableExpiryWatcher bool, certPathForTLS string) *Redis {
	opt := &redis.Options{
		Addr:     endpoint,
		Password: password,
		DB:       0,
	}
	rdb := redis.NewClient(opt)
	rc := &Redis{
		Client: rdb,
	}
	return rc
}

func (r *Redis) Get(ctx context.Context, key string) ([]byte, error) {
	stringCmd, err := r.Client.Get(ctx, key).Result()
	if err != nil {
		return nil, err
	}
	return []byte(stringCmd), nil
}

func (r *Redis) Create(ctx context.Context, key string, value interface{}, ttl time.Duration) error {
	r.Client.Del(ctx, key)
	cmd := r.Client.Set(ctx, key, value, ttl)
	if cmd.Err() != nil {
		return cmd.Err()
	}
	return nil
}

func (r *Redis) Delete(ctx context.Context, key string) error {
	_, err := r.Client.Del(ctx, key).Result()
	if err != nil {
		return err
	}
	return nil
}

func (r *Redis) Ping(ctx context.Context) error {
	_, err := r.Client.Ping(ctx).Result()
	if err != nil {
		return err
	}
	return nil
}
func (r *Redis) Exists(ctx context.Context, key string) bool {
	v, err := r.Client.Exists(ctx, key).Result()
	if err != nil {
		return false
	}
	if v > 0 {
		return true
	}
	return false
}
