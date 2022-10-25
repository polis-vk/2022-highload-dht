# Запуск

Сервер

```shell
./gradlew clean run --args=http://localhost:19234
```

`async-profiler` и `wrk` 

```shell
sudo sysctl kernel.kptr_restrict=0
sudo sysctl kernel.perf_event_paranoid=1
bash -x test.sh 2>&1 | tee test.out
```