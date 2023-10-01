// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package memory

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"github.com/harness/harness-core/product/log-service/cache"
)

var _ cache.Cache = (*Cacher)(nil)

type Cacher struct {
	sync.Mutex
	cache map[string]interface{}
}

func New() *Cacher {
	return &Cacher{
		cache: make(map[string]interface{}),
	}
}

func (c Cacher) Create(ctx context.Context, key string, i interface{}, ttl time.Duration) error {
	c.Lock()
	defer c.Unlock()
	c.cache[key] = i
	return nil
}

func (c Cacher) Get(ctx context.Context, key string) ([]byte, error) {
	c.Lock()
	defer c.Unlock()
	if _, ok := c.cache[key]; ok {
		item := c.cache[key]
		return json.Marshal(item)
	}
	return nil, errors.New("item not found")
}

func (c Cacher) Ping(ctx context.Context) error {
	return nil
}

func (c Cacher) Exists(ctx context.Context, key string) bool {
	c.Lock()
	defer c.Unlock()
	_, ok := c.cache[key]
	return ok
}

func (c Cacher) Delete(ctx context.Context, key string) error {
	c.Lock()
	defer c.Unlock()
	if _, ok := c.cache[key]; ok {
		delete(c.cache, key)
		return nil
	}
	return errors.New("item not found")
}
