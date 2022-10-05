Обратите внимание:
* При профилировании метода GET бд наполнялась 2 млн пар ключ-значение и имела размер 1GB.
* При профилировании метода PUT бд изначально была пустой.
* Использовались две реализации очереди: Queue (LinkedBlockingQueue) и Stack (на основе LinkedBlockingDeque).
* Скрипты для wrk2: [get](scripts/get.lua) и [put](scripts/put.lua)
