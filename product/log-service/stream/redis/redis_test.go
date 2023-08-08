// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"log"
	"os"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/go-redis/redismock/v9"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"

	"github.com/harness/harness-core/product/log-service/stream"
)

var (
	client *redis.Client
)

// BufioWriterCloser combines a bufio Writer with a Closer
type BufioWriterCloser struct {
	*bufio.Writer
}

func (bwc *BufioWriterCloser) Close() error {
	if err := bwc.Flush(); err != nil {
		return err
	}
	return nil
}

func TestMain(m *testing.M) {
	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	code := m.Run()
	os.Exit(code)
}

func TestCreate(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: client,
	}

	rdb.Create(ctx, key)

	assert.Equal(t, mr.Exists(key), true)
}

func TestCreate_Error(t *testing.T) {

	ctx := context.Background()
	key := "key"

	cli, mock := redismock.NewClientMock()
	args := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Values: map[string]interface{}{entryKey: []byte{}},
	}
	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectDel([]string{key}...).SetVal(1)
	mock.ExpectXAdd(args).SetErr(errors.New("err"))

	rdb := &Redis{
		Client: cli,
	}
	err := rdb.Create(ctx, key)
	assert.NotEqual(t, err, nil)
}

func TestDelete(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: client,
	}

	mr.XAdd(key, "*", []string{"k1", "v1"})

	err = rdb.Delete(ctx, key)

	assert.Equal(t, mr.Exists(key), false)
	assert.Equal(t, err, nil)
}

func TestDelete_DoesntExist(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: client,
	}
	err = rdb.Delete(ctx, key)
	assert.Equal(t, mr.Exists(key), false)
	assert.Equal(t, err, stream.ErrNotFound)
}

func TestWrite_Success(t *testing.T) {
	ctx := context.Background()
	key := "key"

	cli, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	args1 := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Approx: true,
		Values: map[string]interface{}{entryKey: bytes1},
	}
	args2 := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Approx: true,
		Values: map[string]interface{}{entryKey: bytes2},
	}
	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectXAdd(args1).SetVal("success")
	mock.ExpectXAdd(args2).SetVal("success")
	mock.ExpectTTL(key).SetVal(20 * time.Second)

	rdb := &Redis{
		Client: cli,
	}
	err := rdb.Write(ctx, key, line1, line2)
	assert.Equal(t, err, nil)
}

func TestWrite_Failure(t *testing.T) {
	ctx := context.Background()
	key := "key"

	cli, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	line3 := &stream.Line{Level: "error", Number: 2, Message: "test message3"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	bytes3, _ := json.Marshal(&line3)
	args1 := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Values: map[string]interface{}{entryKey: bytes1},
	}
	args2 := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Values: map[string]interface{}{entryKey: bytes2},
	}
	args3 := &redis.XAddArgs{
		Stream: key,
		ID:     "*",
		MaxLen: maxStreamSize,
		Values: map[string]interface{}{entryKey: bytes3},
	}
	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectXAdd(args1).SetVal("success")
	mock.ExpectXAdd(args2).SetErr(errors.New("err"))
	mock.ExpectXAdd(args3).SetVal("success")
	mock.ExpectTTL(key).SetVal(20 * time.Second)

	rdb := &Redis{
		Client: cli,
	}
	err := rdb.Write(ctx, key, line1, line2, line3)
	assert.NotEqual(t, err, nil)
}

func TestTail_Single_Success(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	key := "key"

	cli, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	args := &redis.XReadArgs{
		Streams: append([]string{key}, "0"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: key,
			Messages: []redis.XMessage{
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes1)}},
				{ID: "2", Values: map[string]interface{}{entryKey: string(bytes2)}},
			},
		},
	}

	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectXRead(args).SetVal(stream)

	rdb := &Redis{
		Client: cli,
	}
	linec, _ := rdb.Tail(ctx, key)
	time.Sleep(100 * time.Millisecond)
	cancel()
	lineResponse1 := <-linec
	assert.Equal(t, lineResponse1, line1)
	lineResponse2 := <-linec
	assert.Equal(t, lineResponse2, line2)
}

func TestTail_Multiple(t *testing.T) {
	ctx, _ := context.WithCancel(context.Background())
	key := "key"

	cli, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)

	args1 := &redis.XReadArgs{
		Streams: append([]string{key}, "0"),
		Block:   readPollTime,
	}
	args2 := &redis.XReadArgs{
		Streams: append([]string{key}, "1"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: key,
			Messages: []redis.XMessage{
				{ID: "0", Values: map[string]interface{}{entryKey: string(bytes1)}},
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes2)}},
			},
		},
	}

	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectXRead(args1).SetVal(stream)
	mock.ExpectXRead(args2).SetErr(errors.New("err"))

	rdb := &Redis{
		Client: cli,
	}

	linec, errc := rdb.Tail(ctx, key)
	lineResponse1 := <-linec
	assert.Equal(t, lineResponse1, line1)
	lineResponse2 := <-linec
	assert.Equal(t, lineResponse2, line2)
	err := <-errc
	assert.Equal(t, err, errors.New("err"))
}

func TestTail_Failure(t *testing.T) {
	ctx := context.Background()
	key := "key"

	cli, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	lines := []*stream.Line{line1, line2}
	bytes, _ := json.Marshal(&lines)
	args := &redis.XReadArgs{
		Streams: append([]string{key}, "0"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: key,
			Messages: []redis.XMessage{
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes)}},
			},
		},
	}

	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectXRead(args).SetVal(stream)

	rdb := &Redis{
		Client: cli,
	}
	_, errc := rdb.Tail(ctx, key)
	err := <-errc
	assert.NotEqual(t, err, nil)
	assert.Equal(t, err, errors.New("all "+
		"expectations were already fulfilled, "+
		"call to cmd '[xread block 100 streams key 1]'"+
		" was not expected"))
}

func TestCopyTo(t *testing.T) {
	ctx, _ := context.WithCancel(context.Background())
	key := "key"

	cli, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message1"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	args := &redis.XReadArgs{
		Streams: append([]string{key}, "0"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: key,
			Messages: []redis.XMessage{
				{ID: "0", Values: map[string]interface{}{entryKey: string(bytes1)}},
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes2)}},
			},
		},
	}

	mock.ExpectExists([]string{key}...).SetVal(1)
	mock.ExpectXRead(args).SetVal(stream)

	rdb := &Redis{
		Client: cli,
	}
	w := new(bytes.Buffer)
	bwc := BufioWriterCloser{bufio.NewWriter(w)}
	err := rdb.CopyTo(ctx, key, &bwc)

	assert.Equal(t, err, nil)
	bytes1 = append(bytes1, []byte("\n")...)
	bytes2 = append(bytes2, []byte("\n")...)
	assert.Equal(t, w.Bytes(), append(bytes1, bytes2...))
}

func TestInfo(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: client,
	}
	mr.XAdd(key, "*", []string{"k1", "v1"})

	info := rdb.Info(ctx)
	assert.Equal(t, info.Streams[key].Size, 1)
	assert.Equal(t, info.Streams[key].Subs, -1) // No subs information for Redis
}

func TestExists(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: client,
	}

	mr.XAdd(key, "*", []string{"k1", "v1"})
	assert.Equal(t, mr.Exists(key), true)
	assert.Nil(t, rdb.Exists(ctx, key))
	err = rdb.Delete(ctx, key)

	assert.Equal(t, mr.Exists(key), false)
	assert.NotNil(t, rdb.Exists(ctx, key))
}

func TestListPrefixes(t *testing.T) {
	ctx := context.Background()

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: client,
	}

	mr.XAdd("key1", "*", []string{"k1", "v1"})
	mr.XAdd("key2", "*", []string{"k1", "v1"})
	mr.XAdd("differentPrefix", "*", []string{"k1", "v1"})

	l, err := rdb.ListPrefix(ctx, "key", 1000)
	assert.Nil(t, err)
	assert.Equal(t, len(l), 2)
	assert.Contains(t, l, "key1")
	assert.Contains(t, l, "key2")
}
