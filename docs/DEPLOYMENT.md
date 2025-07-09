# Руководство по развертыванию - OHLC Cache

## Обзор

Данное руководство описывает процесс развертывания и эксплуатации системы OHLC Cache в различных средах.

## Требования к системе

### Минимальные требования

- **CPU:** 4 ядра (рекомендуется 8+)
- **RAM:** 8GB (рекомендуется 16GB+)
- **Диск:** 100GB SSD (рекомендуется NVMe)
- **OS:** Linux (Ubuntu 20.04+, CentOS 8+), macOS 12+, Windows 10+
- **Java:** OpenJDK 21 или Oracle JDK 21

### Рекомендуемые требования

- **CPU:** 16 ядер
- **RAM:** 32GB
- **Диск:** 500GB NVMe SSD
- **Сеть:** 10Gbps Ethernet

## Подготовка к развертыванию

### 1. Установка Java

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

#### CentOS/RHEL
```bash
sudo yum install java-21-openjdk-devel
```

#### macOS
```bash
brew install openjdk@21
```

#### Windows
Скачайте и установите OpenJDK 21 с официального сайта.

### 2. Установка Maven

#### Ubuntu/Debian
```bash
sudo apt install maven
```

#### CentOS/RHEL
```bash
sudo yum install maven
```

#### macOS
```bash
brew install maven
```

### 3. Проверка установки
```bash
java -version
mvn -version
```

## Сборка проекта

### 1. Клонирование репозитория
```bash
git clone <repository-url>
cd Ohlc_Cache
```

### 2. Сборка
```bash
mvn clean install -DskipTests
```

### 3. Создание JAR файла
```bash
mvn spring-boot:repackage
```

## Конфигурация

### Основные настройки

Создайте файл `application-prod.yaml`:

```yaml
spring:
  application:
    name: Ohlc_Cache
  profiles:
    active: prod

server:
  port: 8080
  compression:
    enabled: true
    mime-types: application/json,application/xml,text/html,text/xml,text/plain

cache:
  disable: false
  chronicle:
    time-series:
      base-path: "/data/ohlc-cache/timeseries-chronicle"
  index:
    name: chronicle
    time-series:
      queue-size: 262144  # Увеличено для продакшена
      base-path: "/data/ohlc-cache/timeseries-index"

logging:
  level:
    com.ob.ohlc_cache: INFO
    net.openhft: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/ohlc-cache/application.log
    max-size: 100MB
    max-history: 30
```

### Системные свойства

Создайте файл `system-prod.properties`:

```properties
# Трассировка ресурсов
jvm.resource.tracing=false
disable.resource.warning=true

# Проверка потокобезопасности
check.thread.safety=false

# Отключение safepoint для производительности
jvm.safepoint.enabled=false

# Отключение предупреждений
warnAndCloseIfNotClosed=false

# Отключение логирования announcer
chronicle.announcer.disable=true

# Оптимизации для продакшена
dumpCodeToTarget=false
```

## Развертывание

### 1. Создание директорий
```bash
sudo mkdir -p /data/ohlc-cache/{timeseries-chronicle,timeseries-index}
sudo mkdir -p /var/log/ohlc-cache
sudo chown -R $USER:$USER /data/ohlc-cache /var/log/ohlc-cache
```

### 2. Копирование файлов
```bash
cp target/Ohlc_Cache-0.0.1.jar /opt/ohlc-cache/
cp application-prod.yaml /opt/ohlc-cache/
cp system-prod.properties /opt/ohlc-cache/
```

### 3. Создание systemd сервиса

Создайте файл `/etc/systemd/system/ohlc-cache.service`:

```ini
[Unit]
Description=OHLC Cache Service
After=network.target

[Service]
Type=simple
User=ohlc-cache
Group=ohlc-cache
WorkingDirectory=/opt/ohlc-cache
ExecStart=/usr/bin/java -Xms4g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dspring.config.location=classpath:/,file:./application-prod.yaml -Dsystem.properties.file=./system-prod.properties -jar Ohlc_Cache-0.0.1.jar
ExecStop=/bin/kill -TERM $MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=ohlc-cache

# Ограничения ресурсов
LimitNOFILE=65536
LimitNPROC=32768

# Переменные окружения
Environment=JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UseZGC"

[Install]
WantedBy=multi-user.target
```

### 4. Создание пользователя
```bash
sudo useradd -r -s /bin/false ohlc-cache
sudo chown -R ohlc-cache:ohlc-cache /opt/ohlc-cache /data/ohlc-cache /var/log/ohlc-cache
```

### 5. Запуск сервиса
```bash
sudo systemctl daemon-reload
sudo systemctl enable ohlc-cache
sudo systemctl start ohlc-cache
```

## Мониторинг

### 1. Проверка статуса
```bash
sudo systemctl status ohlc-cache
```

### 2. Просмотр логов
```bash
# Systemd логи
sudo journalctl -u ohlc-cache -f

# Файловые логи
tail -f /var/log/ohlc-cache/application.log
```

### 3. Метрики производительности

#### JVM метрики
```bash
# Использование памяти
jstat -gc <pid> 1000

# Потоки
jstack <pid> > threads.txt
```

#### Системные метрики
```bash
# Использование CPU и памяти
top -p <pid>

# Дисковые операции
iotop -p <pid>

# Сетевые соединения
netstat -tulpn | grep <pid>
```

## Масштабирование

### Горизонтальное масштабирование

#### 1. Балансировка нагрузки
```nginx
upstream ohlc_cache {
    server 192.168.1.10:8080;
    server 192.168.1.11:8080;
    server 192.168.1.12:8080;
}

server {
    listen 80;
    server_name ohlc-cache.example.com;
    
    location / {
        proxy_pass http://ohlc_cache;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

#### 2. Разделение данных
- Разделение по символам/рынкам
- Использование разных инстансов для разных периодов
- Репликация критических данных

### Вертикальное масштабирование

#### 1. Увеличение памяти
```bash
# В systemd сервисе
ExecStart=/usr/bin/java -Xms8g -Xmx32g ...
```

#### 2. Оптимизация GC
```bash
# G1GC настройки
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=32m
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
```

## Резервное копирование

### 1. Скрипт резервного копирования
```bash
#!/bin/bash
# backup.sh

BACKUP_DIR="/backup/ohlc-cache"
DATE=$(date +%Y%m%d_%H%M%S)

# Остановка сервиса
sudo systemctl stop ohlc-cache

# Создание резервной копии
tar -czf "$BACKUP_DIR/ohlc-cache_$DATE.tar.gz" /data/ohlc-cache

# Запуск сервиса
sudo systemctl start ohlc-cache

# Удаление старых резервных копий (старше 30 дней)
find $BACKUP_DIR -name "ohlc-cache_*.tar.gz" -mtime +30 -delete
```

### 2. Автоматическое резервное копирование
```bash
# Добавить в crontab
0 2 * * * /opt/ohlc-cache/backup.sh
```

## Обновление

### 1. Подготовка к обновлению
```bash
# Создание резервной копии
sudo systemctl stop ohlc-cache
tar -czf backup_$(date +%Y%m%d_%H%M%S).tar.gz /data/ohlc-cache
```

### 2. Обновление приложения
```bash
# Копирование нового JAR
cp target/Ohlc_Cache-0.0.2.jar /opt/ohlc-cache/

# Обновление конфигурации (если необходимо)
cp application-prod.yaml /opt/ohlc-cache/

# Запуск сервиса
sudo systemctl start ohlc-cache
```

### 3. Проверка обновления
```bash
# Проверка статуса
sudo systemctl status ohlc-cache

# Проверка логов
sudo journalctl -u ohlc-cache -f
```

## Устранение неполадок

### Частые проблемы

#### 1. Недостаточно памяти
```
Error: java.lang.OutOfMemoryError: Java heap space
```
**Решение:** Увеличить `-Xmx` параметр в systemd сервисе.

#### 2. Недостаточно файловых дескрипторов
```
Error: java.io.IOException: Too many open files
```
**Решение:** Увеличить `LimitNOFILE` в systemd сервисе.

#### 3. Проблемы с диском
```
Error: java.io.IOException: No space left on device
```
**Решение:** Очистить старые данные или увеличить дисковое пространство.

#### 4. Высокая нагрузка на кэш
```
Error: Cache is under high load, please try again later
```
**Решение:** 
- Увеличить размер очереди индексации
- Добавить дополнительные инстансы
- Оптимизировать запросы

### Диагностика

#### 1. Анализ логов
```bash
# Поиск ошибок
grep -i error /var/log/ohlc-cache/application.log

# Поиск предупреждений
grep -i warn /var/log/ohlc-cache/application.log
```

#### 2. Анализ производительности
```bash
# Профилирование JVM
jmap -dump:format=b,file=heap.hprof <pid>

# Анализ потоков
jstack <pid> > thread_dump.txt
```

#### 3. Мониторинг системы
```bash
# Использование ресурсов
htop
iotop
nethogs
```

## Безопасность

### 1. Сетевая безопасность
- Использование firewall для ограничения доступа
- Настройка SSL/TLS для HTTPS
- Ограничение доступа по IP

### 2. Файловая безопасность
- Правильные права доступа к файлам
- Шифрование чувствительных данных
- Регулярное обновление системы

### 3. Мониторинг безопасности
- Логирование всех операций
- Мониторинг подозрительной активности
- Регулярные аудиты безопасности

## Производительность

### Рекомендации по оптимизации

#### 1. JVM настройки
```bash
# Оптимальные настройки для высокой производительности
-Xms8g -Xmx32g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+UseZGC
-XX:+UseTransparentHugePages
```

#### 2. Системные настройки
```bash
# Увеличение лимитов
echo "* soft nofile 65536" >> /etc/security/limits.conf
echo "* hard nofile 65536" >> /etc/security/limits.conf

# Оптимизация сети
echo "net.core.rmem_max = 16777216" >> /etc/sysctl.conf
echo "net.core.wmem_max = 16777216" >> /etc/sysctl.conf
```

#### 3. Мониторинг производительности
- Регулярный анализ метрик
- Настройка алертов
- Оптимизация на основе данных 