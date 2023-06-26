package redis

import "github.com/go-redis/redis/v7"

type Redis struct {
	Client redis.Cmdable
}

func (r Redis) Publish(s string) error {
	//TODO implement me
	panic("implement me")
}

func (r Redis) Claim(s string) (string, error) {
	//TODO implement me
	panic("implement me")
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
