// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package redis provides a log streaming engine backed by
// a Redis database
package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"time"

	"github.com/go-co-op/gocron"

	"github.com/harness/harness-core/product/log-service/stream"

	// TODO (vistaar): Move to redis v8. v8 accepts ctx in all calls.
	// There is some bazel issue with otel library with v8, need to move it once that is resolved.
	"github.com/pkg/errors"
	"github.com/redis/go-redis/v9"
	"github.com/sirupsen/logrus"
)

const (
	defaultKeyExpiryTimeSeconds = 5 * 60 * 60 // We keep each key in redis for 5 hours
	// Polling time for each thread to wait for read before getting freed up. This should not be too large to avoid
	// redis clients getting occupied for long.
	readPollTime  = 100 * time.Millisecond
	tailMaxTime   = 1 * time.Hour // maximum duration a tail can last
	bufferSize    = 50            // buffer for slow consumers
	maxStreamSize = 5000          // Maximum number of entries in each stream (ring buffer)
	// Maximum number of keys that we will return with a given prefix. If there are more than maxPrefixes keys with a given prefix,
	// only the first maxPrefixes keys will be returned.
	maxPrefixes = 200
	entryKey    = "line"

	// Redis TTL error values
	TTL_NOT_SET          = -1
	TTL_KEY_DOESNT_EXIST = -2
)

type Redis struct {
	Client redis.Cmdable
}

func NewWithClient(cmdable *redis.Cmdable, disableExpiryWatcher bool, scanBatch int64) *Redis {
	rc := &Redis{Client: *cmdable}
	if !disableExpiryWatcher {
		logrus.Infof("starting expiry watcher thread on Redis instance")
		s := gocron.NewScheduler(time.UTC)
		s.Every(defaultKeyExpiryTimeSeconds).Seconds().Do(rc.expiryWatcher, defaultKeyExpiryTimeSeconds*time.Second, scanBatch)
		s.StartAsync()
	}
	return rc
}

// Create creates a redis stream and sets an expiry on it.
func (r *Redis) Create(ctx context.Context, key string) error {
	// Delete if a stream already exists with the same key
	r.Delete(ctx, key)

	// Insert a dummy entry into the stream
	// Trimming with MaxLen can be expensive. We use MaxLenApprox here -
	// trimming is done in the radix tree only when we can remove a whole
	// macro node. MaxLen will always be >= 5000 but can be a few tens of entries
	// more as well.
	args := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Approx: true,
		Values: map[string]interface{}{entryKey: []byte{}},
	}
	resp := r.Client.XAdd(ctx, args)
	if err := resp.Err(); err != nil {
		return errors.Wrap(err, fmt.Sprintf("could not create stream with key: %s", key))
	}

	r.setExpiry(key, defaultKeyExpiryTimeSeconds*time.Second)
	return nil
}

// Delete deletes a stream
func (r *Redis) Delete(ctx context.Context, key string) error {
	exists := r.Client.Exists(ctx, key)
	if exists.Err() != nil || exists.Val() == 0 {
		return stream.ErrNotFound
	}

	resp := r.Client.Del(ctx, key)
	if err := resp.Err(); err != nil {
		return errors.Wrap(err, fmt.Sprintf("could not delete stream with key: %s", key))
	}
	return nil
}

// Write writes information into the Redis stream
func (r *Redis) Write(ctx context.Context, key string, lines ...*stream.Line) error {
	var werr error
	exists := r.Client.Exists(ctx, key)
	if exists.Err() != nil || exists.Val() == 0 {
		return stream.ErrNotFound
	}

	// Write input to redis stream. "*" tells Redis to auto-generate a unique incremental ID.
	for _, line := range lines {
		bytes, _ := json.Marshal(line)
		arg := &redis.XAddArgs{
			Stream: key,
			Values: map[string]interface{}{entryKey: bytes},
			MaxLen: maxStreamSize,
			Approx: true,
			ID:     "*",
		}
		resp := r.Client.XAdd(ctx, arg)
		if err := resp.Err(); err != nil {
			werr = fmt.Errorf("could not write to stream with key: %s. Error: %s", key, err)
		}
	}

	r.setExpiry(key, defaultKeyExpiryTimeSeconds*time.Second)
	return werr
}

// Read returns back all the lines in the stream. If tail is specifed as true, it keeps watching and doesn't
// close the channel.
func (r *Redis) Tail(ctx context.Context, key string) (<-chan *stream.Line, <-chan error) {
	handler := make(chan *stream.Line, bufferSize)
	err := make(chan error, 1)
	exists := r.Client.Exists(ctx, key)
	if exists.Err() != nil || exists.Val() == 0 {
		return nil, nil
	}
	go func() {
		// Keep reading from the stream and writing to the channel
		lastID := "0"
		defer close(err)
		defer close(handler)
		tailMaxTimeTimer := time.After(tailMaxTime) // polling should not last for longer than tailMaxTime
	L:
		for {
			select {
			case <-ctx.Done():
				break L
			case <-tailMaxTimeTimer:
				break L
			default:
				args := &redis.XReadArgs{
					Streams: append([]string{key}, lastID),
					Block:   readPollTime, // periodically check for ctx.Done
				}

				resp := r.Client.XRead(ctx, args)
				if resp.Err() != nil && resp.Err() != redis.Nil { // resp.Err() is sometimes set to "redis: nil" instead of nil
					logrus.WithError(resp.Err()).Errorln("received error on redis read call")
					err <- resp.Err()
					break L
				}

				for _, msg := range resp.Val() {
					b := msg.Messages
					if len(b) > 0 {
						lastID = b[len(b)-1].ID
					} else { // Should not happen
						break L
					}
					for _, message := range b {
						x := message.Values
						if val, ok := x[entryKey]; ok {
							var in *stream.Line
							if err := json.Unmarshal([]byte(val.(string)), &in); err != nil {
								// Ignore errors in the stream
								continue
							}
							handler <- in
						}
					}
				}
			}
		}
	}()
	return handler, err
}

// Exists checks whether the key exists in the stream
func (r *Redis) Exists(ctx context.Context, key string) error {
	exists := r.Client.Exists(ctx, key)
	if exists.Err() != nil || exists.Val() == 0 {
		return stream.ErrNotFound
	}
	return nil
}

func (r *Redis) ListPrefix(ctx context.Context, prefix string, scanBatch int64) ([]string, error) {
	// Return all the keys with the given prefix
	l := []string{}
	if len(prefix) == 0 {
		return l, nil
	}
	if prefix[len(prefix)-1] != '*' {
		prefix = prefix + "*"
	}

	var cursor uint64
	keyM := make(map[string]struct{})
	for {
		var keys []string
		var err error
		// Scan keys in batches of size scanBatch at a time
		keys, cursor, err = r.Client.Scan(ctx, cursor, prefix, scanBatch).Result()
		if err != nil {
			return l, err
		}
		for _, k := range keys {
			if _, ok := keyM[k]; ok {
				continue
			}
			keyM[k] = struct{}{}
			l = append(l, k)
		}
		if cursor == 0 || len(l) > maxPrefixes {
			break
		}
	}

	return l, nil
}

// CopyTo copies the contents from the redis stream to the writer
func (r *Redis) CopyTo(ctx context.Context, key string, wc io.WriteCloser) error {
	defer wc.Close()
	exists := r.Client.Exists(ctx, key)
	if exists.Err() != nil || exists.Val() == 0 {
		return stream.ErrNotFound
	}

	lastID := "0"
	args := &redis.XReadArgs{
		Streams: append([]string{key}, lastID),
		Block:   readPollTime, // periodically check for ctx.Done
	}

	resp := r.Client.XRead(ctx, args)
	if resp.Err() != nil && resp.Err() != redis.Nil { // resp.Err() is sometimes set to "redis: nil" instead of nil
		logrus.WithError(resp.Err()).Errorln("received error on redis read call")
		return resp.Err()
	}

	for _, msg := range resp.Val() {
		b := msg.Messages
		if len(b) > 0 {
			lastID = b[len(b)-1].ID
		} else { // Should not happen
			break
		}
		for _, message := range b {
			x := message.Values
			if val, ok := x[entryKey]; ok && val.(string) != "" {
				wc.Write([]byte(val.(string)))
				wc.Write([]byte("\n"))
			}
		}
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

// Info returns back information like TTL, size of a stream
// NOTE: This is super slow for Redis and hogs up all the resources.
// TODO: (vistaar) Return only top x entries
func (r *Redis) Info(ctx context.Context) *stream.Info {
	resp := r.Client.Keys(ctx, "*") // Get all keys
	info := &stream.Info{
		Streams: map[string]stream.Stats{},
	}
	for _, key := range resp.Val() {
		ttl := "-1" // default
		size := -1  // default
		ttlResp := r.Client.TTL(ctx, key)
		if err := ttlResp.Err(); err == nil {
			ttl = ttlResp.Val().String()
		}
		lenResp := r.Client.XLen(ctx, key)
		if err := lenResp.Err(); err == nil {
			size = int(lenResp.Val())
		}
		info.Streams[key] = stream.Stats{
			Size: size, // Note: this is not the actual number of lines. Each key-value pair consists of multiple lines.
			// This is done to prevent minimum number of calls to Redis.
			Subs: -1, // no sub information for redis streams
			TTL:  ttl,
		}
	}
	return info
}

// Helper function to set an expiry to a key if it's not already set
func (r *Redis) setExpiry(key string, expiry time.Duration) error {
	ttl := r.Client.TTL(context.Background(), key)
	if ttl.Err() != nil {
		logrus.Errorf("could not retrieve TTL for key: %s. Error: %s", key, ttl.Err())
		return ttl.Err()
	}

	resp, err := ttl.Result()
	if err != nil {
		logrus.Errorf("could not retrieve result for key: %s. Error: %s", key, err)
		return err
	}

	// Only set an expiry if the expiry is not already set
	if resp == TTL_KEY_DOESNT_EXIST {
		return errors.New("could not set expiry as key doesn't exist")
	} else if resp == TTL_NOT_SET {
		// Set a TTL for the stream
		res := r.Client.Expire(context.Background(), key, expiry)
		if err := res.Err(); err != nil {
			logrus.Errorf("could not set expiry on key: %s. Error: %s", key, err)
			return errors.Wrap(err, fmt.Sprintf("could not set expiry for key: %s", key))
		}
	} else {
		return errors.New("could not set expiry as it is already set")
	}
	return nil
}

// Scan all the keys and set an expiry on them if it's not set
func (r *Redis) expiryWatcher(expiry time.Duration, scanBatch int64) {
	logrus.Infof("running expiry watcher thread")
	st := time.Now()
	var cursor uint64
	cnt := 0
	for {
		var keys []string
		var err error
		// Scan keys in batches of size scanBatch at a time
		keys, cursor, err = r.Client.Scan(context.Background(), cursor, "*", scanBatch).Result()
		if err != nil {
			logrus.Error(errors.Wrap(err, "error in expiry watcher thread"))
			return
		}
		for _, k := range keys {
			if err := r.setExpiry(k, expiry); err == nil {
				logrus.Infof("set an expiry %s on non-volatile key: %s", expiry, k)
				cnt++
			}
		}
		if cursor == 0 {
			break
		}
	}
	logrus.Infof("done running expiry watcher thread in %s time and expired %d keys", time.Since(st), cnt)
}
