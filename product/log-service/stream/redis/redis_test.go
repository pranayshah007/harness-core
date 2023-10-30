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
	"fmt"
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
	client        *redis.Client
	maxStreamSize int64 = 10
	maxLineLimit  int64 = 50
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

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)

	rdb.Create(ctx, key)

	assert.Equal(t, mr.Exists(stream.Prefix+key), true)
}

func TestCreate_Error(t *testing.T) {

	ctx := context.Background()
	key := "key"

	client, mock := redismock.NewClientMock()
	args := &redis.XAddArgs{
		Stream: createLogStreamPrefixedKey(key),
		ID:     "*",
		MaxLen: maxStreamSize,
		Values: map[string]interface{}{entryKey: []byte{}},
	}
	mock.ExpectExists([]string{createLogStreamPrefixedKey(key)}...).SetVal(1)
	mock.ExpectDel([]string{createLogStreamPrefixedKey(key)}...).SetVal(1)
	mock.ExpectXAdd(args).SetErr(errors.New("err"))

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
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

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)

	mr.XAdd(createLogStreamPrefixedKey(key), "*", []string{"k1", "v1"})

	err = rdb.Delete(ctx, key)

	assert.Equal(t, mr.Exists(createLogStreamPrefixedKey(key)), false)
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

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
	err = rdb.Delete(ctx, key)
	assert.Equal(t, mr.Exists(createLogStreamPrefixedKey(key)), false)
	assert.Equal(t, err, stream.ErrNotFound)
}

func TestWrite_Success(t *testing.T) {
	ctx := context.Background()
	key := "key"

	client, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	args1 := &redis.XAddArgs{
		Stream:     createLogStreamPrefixedKey(key),
		ID:         "*",
		MaxLen:     maxStreamSize,
		Approx:     true,
		Values:     map[string]interface{}{entryKey: bytes1},
		NoMkStream: true,
	}
	args2 := &redis.XAddArgs{
		Stream:     createLogStreamPrefixedKey(key),
		ID:         "*",
		MaxLen:     maxStreamSize,
		Approx:     true,
		Values:     map[string]interface{}{entryKey: bytes2},
		NoMkStream: true,
	}
	mock.ExpectExists([]string{createLogStreamPrefixedKey(key)}...).SetVal(1)
	mock.ExpectXAdd(args1).SetVal("success")
	mock.ExpectXAdd(args2).SetVal("success")
	mock.ExpectTTL(createLogStreamPrefixedKey(key)).SetVal(20 * time.Second)

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
	err := rdb.Write(ctx, key, line1, line2)
	assert.Equal(t, err, nil)
}

func TestWrite_Failure(t *testing.T) {
	ctx := context.Background()
	key := "key"
	internalKey := createLogStreamPrefixedKey(key)

	client, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	line3 := &stream.Line{Level: "error", Number: 2, Message: "test message3"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	bytes3, _ := json.Marshal(&line3)
	args1 := &redis.XAddArgs{
		Stream:     internalKey,
		ID:         "*",
		MaxLen:     maxStreamSize,
		Values:     map[string]interface{}{entryKey: bytes1},
		NoMkStream: true,
	}
	args2 := &redis.XAddArgs{
		Stream:     internalKey,
		ID:         "*",
		MaxLen:     maxStreamSize,
		Values:     map[string]interface{}{entryKey: bytes2},
		NoMkStream: true,
	}
	args3 := &redis.XAddArgs{
		Stream:     internalKey,
		ID:         "*",
		MaxLen:     maxStreamSize,
		Values:     map[string]interface{}{entryKey: bytes3},
		NoMkStream: true,
	}
	mock.ExpectExists([]string{internalKey}...).SetVal(1)
	mock.ExpectXAdd(args1).SetVal("success")
	mock.ExpectXAdd(args2).SetErr(errors.New("err"))
	mock.ExpectXAdd(args3).SetVal("success")
	mock.ExpectTTL(internalKey).SetVal(20 * time.Second)

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
	err := rdb.Write(ctx, key, line1, line2, line3)
	fmt.Println("error: ", err)
	assert.NotEqual(t, err, nil)
}

func TestTail_Single_Success(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	key := "key"
	internalKey := createLogStreamPrefixedKey(key)

	client, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	args := &redis.XReadArgs{
		Streams: append([]string{internalKey}, "0"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: internalKey,
			Messages: []redis.XMessage{
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes1)}},
				{ID: "2", Values: map[string]interface{}{entryKey: string(bytes2)}},
			},
		},
	}

	mock.ExpectExists([]string{internalKey}...).SetVal(1)
	mock.ExpectXRead(args).SetVal(stream)

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
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
	internalKey := createLogStreamPrefixedKey(key)

	client, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)

	args1 := &redis.XReadArgs{
		Streams: append([]string{internalKey}, "0"),
		Block:   readPollTime,
	}
	args2 := &redis.XReadArgs{
		Streams: append([]string{internalKey}, "1"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: internalKey,
			Messages: []redis.XMessage{
				{ID: "0", Values: map[string]interface{}{entryKey: string(bytes1)}},
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes2)}},
			},
		},
	}

	mock.ExpectExists([]string{internalKey}...).SetVal(1)
	mock.ExpectXRead(args1).SetVal(stream)
	mock.ExpectXRead(args2).SetErr(errors.New("err"))

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)

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
	internalKey := createLogStreamPrefixedKey(key)

	client, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message2"}
	lines := []*stream.Line{line1, line2}
	bytes, _ := json.Marshal(&lines)
	args := &redis.XReadArgs{
		Streams: append([]string{internalKey}, "0"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: internalKey,
			Messages: []redis.XMessage{
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes)}},
			},
		},
	}

	mock.ExpectExists([]string{internalKey}...).SetVal(1)
	mock.ExpectXRead(args).SetVal(stream)

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
	_, errc := rdb.Tail(ctx, key)
	err := <-errc
	assert.NotEqual(t, err, nil)
	assert.Equal(t, err, fmt.Errorf("all "+
		"expectations were already fulfilled, "+
		"call to cmd '[xread block 100 streams %s 1]'"+
		" was not expected", internalKey))
}

func TestCopyTo(t *testing.T) {
	ctx, _ := context.WithCancel(context.Background())
	key := "key"
	internalKey := createLogStreamPrefixedKey(key)

	client, mock := redismock.NewClientMock()
	line1 := &stream.Line{Level: "info", Number: 0, Message: "test message"}
	line2 := &stream.Line{Level: "warn", Number: 1, Message: "test message1"}
	bytes1, _ := json.Marshal(&line1)
	bytes2, _ := json.Marshal(&line2)
	args := &redis.XReadArgs{
		Streams: append([]string{internalKey}, "0"),
		Block:   readPollTime,
	}

	stream := []redis.XStream{
		{
			Stream: internalKey,
			Messages: []redis.XMessage{
				{ID: "0", Values: map[string]interface{}{entryKey: string(bytes1)}},
				{ID: "1", Values: map[string]interface{}{entryKey: string(bytes2)}},
			},
		},
	}

	mock.ExpectExists([]string{internalKey}...).SetVal(1)
	mock.ExpectXRead(args).SetVal(stream)

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
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

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)
	mr.XAdd(key, "*", []string{"k1", "v1"})

	info := rdb.Info(ctx)
	assert.Equal(t, info.Streams[key].Size, 1)
	assert.Equal(t, info.Streams[key].Subs, -1) // No subs information for Redis
}

func TestExists(t *testing.T) {
	ctx := context.Background()
	key := "key"
	internalKey := createLogStreamPrefixedKey(key)

	mr, err := miniredis.Run()
	if err != nil {
		log.Fatalf("an error '%s' was not expected when opening a stub database connection", err)
	}
	defer mr.Close()

	client = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)

	mr.XAdd(internalKey, "*", []string{"k1", "v1"})
	assert.Equal(t, mr.Exists(internalKey), true)
	assert.Nil(t, rdb.Exists(ctx, key))
	rdb.Delete(ctx, key)

	assert.Equal(t, mr.Exists(internalKey), false)
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

	rdb := NewWithClient(client, true, 1000, maxLineLimit, maxStreamSize)

	internalKey1 := createLogStreamPrefixedKey("key1")
	internalKey2 := createLogStreamPrefixedKey("key2")

	mr.XAdd(internalKey1, "*", []string{"k1", "v1"})
	mr.XAdd(internalKey2, "*", []string{"k1", "v1"})
	mr.XAdd("differentPrefix", "*", []string{"k1", "v1"})

	l, err := rdb.ListPrefix(ctx, "key", 1000)
	assert.Nil(t, err)
	assert.Equal(t, len(l), 2)
	assert.Contains(t, l, "key1")
	assert.Contains(t, l, "key2")
}

func TestCreatePrefix(t *testing.T) {
	key := "key"
	prefixedKey := createLogStreamPrefixedKey(key)
	assert.Equal(t, prefixedKey, "log-servicekey")

	key = "log-servicexyz"
	prefixedKey = createLogStreamPrefixedKey(key)
	assert.Equal(t, prefixedKey, "log-servicexyz")
}

func TestRemovePrefix(t *testing.T) {
	key := "key"
	prefixedKey := removeLogStreamPrefixedKey(key)
	assert.Equal(t, prefixedKey, "key")

	key = "log-servicexyz"
	prefixedKey = removeLogStreamPrefixedKey(key)
	assert.Equal(t, prefixedKey, "xyz")
}

func TestSanitizeLines(t *testing.T) {
	l1 := &stream.Line{
		Message: "this should not",
	}
	l2 := &stream.Line{
		Message: "here is a string which should be truncated",
	}
	lines := []*stream.Line{l1, l2, l1, l2, l1, l2}
	lines, cnt := sanitizeLines(lines, 20, 3, "key")
	fmt.Println("lines is: ", lines)
	// leftover lines should be l2, l1, l2
	assert.Equal(t, len(lines), 3)
	assert.Equal(t, cnt, 2)
	assert.Equal(t, lines[1].Message, l1.Message) // no truncation
	assert.Equal(t, lines[0].Message, "here is a string whi... (log line truncated)")
	assert.Equal(t, lines[0].Message, lines[2].Message)
}
