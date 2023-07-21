package redis

import (
	"context"
	"encoding/json"
	"errors"
	"strconv"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/go-redis/redismock/v9"
	"github.com/golang/mock/gomock"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
)

var (
	cli *redis.Client
)

type TestStruct struct {
	item int `json:"item"`
}

func (ts TestStruct) MarshalBinary() ([]byte, error) {
	return json.Marshal(ts)
}

func (ts TestStruct) UnmarshalBinary(b []byte) error {
	return json.Unmarshal(b, &ts)
}

func TestRedis_Create(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}

	err = rdb.Create(ctx, key, TestStruct{item: 10}, time.Hour)
	if err != nil {
		t.Error(err)
	}

	assert.Equal(t, mr.Exists(key), true)
}

func TestRedis_Create_Error(t *testing.T) {
	ctx := context.Background()
	key := "key"

	cliMock, mock := redismock.NewClientMock()

	mock.ExpectDel([]string{key}...).SetVal(1)
	mock.ExpectSet(key, gomock.Any(), time.Hour).SetErr(errors.New("err"))

	rdb := &Redis{
		Client: cliMock,
	}

	err := rdb.Create(ctx, key, TestStruct{item: 10}, time.Hour)

	assert.NotNil(t, err, true)
}

func TestRedis_Delete(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}

	err = mr.Set(key, "")
	if err != nil {
		t.Error(err)
	}

	err = rdb.Delete(ctx, key)
	assert.Equal(t, mr.Exists(key), false)
	assert.Nil(t, err)

	err = rdb.Delete(ctx, "notexists")
	assert.Equal(t, mr.Exists("notexists"), false)
}

func TestRedis_Exists(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}

	err = mr.Set(key, "")
	if err != nil {
		t.Error(err)
	}

	exists := rdb.Exists(ctx, key)
	assert.Equal(t, mr.Exists(key), exists)

	exists = rdb.Exists(ctx, "notexists")
	assert.Equal(t, mr.Exists("notexists"), exists)
}

func TestRedis_Get(t *testing.T) {
	ctx := context.Background()
	key := "key"

	mr, err := miniredis.Run()
	if err != nil {
		t.Error(err)
	}
	defer mr.Close()

	cli = redis.NewClient(&redis.Options{
		Addr: mr.Addr(),
	})

	rdb := &Redis{
		Client: cli,
	}

	err = mr.Set(key, "4")
	if err != nil {
		t.Error(err)
	}

	item, err := rdb.Get(ctx, key)
	assert.Equal(t, []byte(strconv.Itoa(4)), item)

	item, err = rdb.Get(ctx, "notexists")
	assert.NotNil(t, err)
	assert.Nil(t, item)
}
