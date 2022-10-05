# Подготовка

## Установка `wrk`

Debian-based distro (e.g. Ubuntu).

```shell
sudo apt update && sudo apt install -y build-essential libssl-dev git zlib1g-dev dos2unix
git clone --single-branch https://github.com/giltene/wrk2.git && cd wrk2

# fixes line endings issue, https://github.com/giltene/wrk2/issues/70
find . -type f -print0 | xargs -0 dos2unix
make
mv wrk ~/.local/bin/ # or add to $PATH
```

## Установка `async-profiler`

```shell
git clone https://github.com/Artyomcool/async-profiler.git --branch=single-page-heatmap && cd async-profiler
export JAVA_HOME="$HOME/.jdks/liberica-17.0.4.1" # need to install jdk for Java 17 with debug symbols
make
chmod u+x ./profiler.sh
ln ./profiler.sh ~/.local/bin/async-profiler
```

# Определение стабильной нагрузки

В одной консоли запускаем сервис:

```shell
# application.mainClass should be 'ok.dht.test.kondraev.Service' in build.gradle
./gradlew clean run --args=http://localhost:19234
```

В другой -- `async-profiler` и `wrk` ([test-01.sh](01/test-01.sh))

```shell
sudo sysctl kernel.kptr_restrict=0
sudo sysctl kernel.perf_event_paranoid=1
bash -x ./test-01.sh http://localhost:19234 2>&1 | tee test-01.out
```

Графики распределения latency, построенный по выводу `wrk2` (сырые данные см. в [test-01.out](01/test-01.out), [put-01.jfr](01/put-01.jfr), [get-01.jfr](01/get-01.jfr), [get-02.jfr]):

![Put](01/Histogram-put.png)

![Get](01/Histogram-get.png)

По этим графикам видно, что на данном этапе стабильной является частота запросов $≈ 10\text{k}/\text{s}$ для `PUT`, и $≈ 4.3\text{k}/\text{s}$ для `GET`.

Heatmap'ы, полученные во время запуска [test-01.sh](01/test-01.sh):

- [put-01-alloc.html](01/put-01-alloc.html)
- [put-01-cpu.html](01/put-01-cpu.html)
- [get-01-alloc.html](01/get-01-alloc.html)
- [get-01-cpu.html](01/get-01-cpu.html)
- [get-02-alloc.html](01/get-01-alloc.html)
- [get-02-cpu.html](01/get-01-cpu.html)

Вывод, который можно сделать сейчас, это,

- интервал профилирования стоит уменьшить (сейчас это `10ms`).
- значительная часть GET занимает доступ к диску через MemorySegment и его создание
- профиль PUT значительно лучше, как и ожидается: большая часть времени занята обработкой syscalls, которые практически не улучшить
- практически все аллокации PUT связаны с преобразованием byte[] ↔ MemorySegment.

# Тестирование на стабильной нагрузке

Сервер:

```shell
./gradlew clean run --args=http://localhost:19234
```

`async-profiler` и `wrk` ([test-02.sh](01/test-02.sh)).

```shell
sudo sysctl kernel.kptr_restrict=0
sudo sysctl kernel.perf_event_paranoid=1
bash -x ./test-02.sh 2>&1 | tee test-02.out
```

## Результат

Heatmap'ы, полученные во время запуска [test-02.sh](01/test-02.sh):

- [put-02-alloc.html](01/put-02-alloc.html)
- [put-02-cpu.html](01/put-02-cpu.html)
- [get-02-alloc.html](01/get-02-alloc.html)
- [get-02-cpu.html](01/get-02-cpu.html)

Распределение latency для PUT:

![Get](01/Histogram-put-2.png)

Распределение latency для GET:

![Get](01/Histogram-get-2.png)

```shell
java -cp ~/dev/async-profiler/build/converter.jar jfr2heat --alloc 01/put-02.jfr 01/put-02-alloc.html
java -cp ~/dev/async-profiler/build/converter.jar jfr2heat 01/put-02.jfr 01/put-02-cpu.html
java -cp ~/dev/async-profiler/build/converter.jar jfr2heat --alloc 01/get-02.jfr 01/get-02-alloc.html
java -cp ~/dev/async-profiler/build/converter.jar jfr2heat 01/get-02.jfr 01/get-02-cpu.html
```

Выводы

- профиль PUT, как и профиль GET практически не изменился.
- не угадали со стабильной частотой запросов в обоих случаях.

Сырые данные: [put-02.jfr](01/put-02.jfr), [get-03.jfr](01/get-02.jfr),
[test-02.out](01/test-02.out).