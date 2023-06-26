package redis

import (
	"context"
	"encoding/json"
	"github.com/go-redis/redis/v7"
	"time"
)

type Redis struct {
	Client redis.Cmdable
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

func (r *Redis) Get(ctx context.Context, key string) (string, error) {
	value, err := r.Client.Get(key).Result()
	if err != nil {
		return "", err
	}
	return value, err
}

type Info struct {
	Value  string
	Status string
}

func (i Info) MarshalBinary() ([]byte, error) {
	return json.Marshal(i)
}

func (i Info) UnmarshalBinary(b []byte) error {
	inf := Info{}
	return json.Unmarshal(b, &inf)
}

func (r *Redis) Create(ctx context.Context, key, value, status string) error {
	r.Client.Del(key)
	err := r.Client.Set(key, Info{
		value,
		status,
	}, time.Hour)
	if err != nil {
		return err.Err()
	}
	return nil
}

func (r *Redis) Ping(ctx context.Context) error {
	_, err := r.Client.Ping().Result()
	if err != nil {
		return err
	}
	return nil
}
