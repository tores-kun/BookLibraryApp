# Библиотека книг - Android приложение

Нативное Android приложение для управления личной библиотекой EPUB книг с серверной синхронизацией.

## Особенности

### 📱 Архитектура
- **MVVM + Clean Architecture** с четким разделением слоев
- **Jetpack Compose** для современного UI
- **Hilt** для dependency injection
- **Room** для локального кэширования 
- **Retrofit + OkHttp** для сетевых запросов
- **Coroutines + Flow** для асинхронности

### 📚 Функциональность
- **Каталог книг** с поиском, фильтрами и сортировкой
- **Скачивание EPUB** с прогрессом и Resume capability
- **Встроенная читалка** с настройками шрифта и темами
- **Text-to-Speech** для озвучивания текста
- **Закладки** с синхронизацией на сервер
- **Заметки** с привязкой к главам
- **Офлайн режим** с Cache-Then-Network стратегией

### 🔒 Безопасность
- **Network Security Config** для самоподписанных сертификатов
- **Cleartext разрешен** только для локального IP 10.93.2.6
- **EncryptedSharedPreferences** для хранения настроек
- **Trust anchors** для HTTPS с локальным CA

## Установка и настройка

### Требования
- Android Studio Arctic Fox или новее
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9+
- Gradle 8.0+

### Сборка проекта

1. **Откройте проект** в Android Studio
2. **Замените сертификат** `app/src/main/res/raw/local_ca.crt` на ваш CA сертификат (для HTTPS профиля)
3. **Настройте URL** в `Constants.kt` при необходимости
4. **Соберите APK**: `./gradlew assembleRelease`

### Настройка сервера

Убедитесь что Flask сервер запущен:

```bash
# HTTP
curl http://10.93.2.6:8080/api/books

# HTTPS (с самоподписанным сертификатом)
curl -k https://10.93.2.6:8888/api/books
```

## Использование

### Первый запуск
1. Установите APK на устройство (разрешите установку из неизвестных источников)
2. Откройте приложение
3. В настройках выберите профиль сети (HTTP/HTTPS)
4. Проверьте соединение с сервером

### Функции приложения

#### Каталог книг
- Поиск по названию и описанию
- Фильтрация по жанрам через chips
- Сортировка по дате, названию, количеству глав
- Быстрые действия: закладка, скачивание

#### Детали книги
- Полная информация о книге с обложкой
- Управление закладками (статус + глава)
- Скачивание EPUB файлов с прогрессом
- Переход к чтению и заметкам

#### Читалка
- Базовый парсинг EPUB файлов
- Навигация по главам
- Настройки размера шрифта (12-24sp)
- Text-to-Speech озвучивание
- Сохранение позиции чтения

## API интеграция

Приложение использует REST API Flask сервера:

- `GET /api/books` - список книг с фильтрацией
- `GET /api/books/{id}` - детали книги
- `POST/DELETE /api/bookmarks` - управление закладками
- `GET/POST/PUT/DELETE /api/notes/*` - CRUD заметок
- `GET /download/{id}` - streaming скачивание EPUB

## Архитектура проекта

```
app/src/main/java/com/booklibrary/android/
├── data/
│   ├── local/          # Room entities, DAO, Database
│   ├── remote/         # API DTOs, Retrofit service
│   ├── repository/     # Repository implementations
│   └── mapper/         # Data mappers
├── domain/
│   ├── model/          # Domain models
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Use cases
├── presentation/
│   ├── ui/screens/     # Compose screens
│   ├── ui/components/  # Reusable UI components
│   ├── viewmodel/      # ViewModels
│   ├── navigation/     # Navigation setup
│   └── theme/          # Material Design theme
├── di/                 # Hilt dependency injection
└── util/               # Utilities, TTS manager
```

## Network Security Config

Для работы с локальным сервером настроен `network_security_config.xml`:

- **HTTP профиль**: разрешает cleartext только для 10.93.2.6
- **HTTPS профиль**: доверяет встроенному CA сертификату

## Зависимости

- **Kotlin 1.9.10** + **Coroutines 1.7.3**
- **Compose BOM 2023.10.01** + **Material3**
- **Hilt 2.48** для DI
- **Room 2.6.1** для локальной БД
- **Retrofit 2.9.0** + **OkHttp 4.12.0**
- **Coil 2.5.0** для загрузки изображений
- **Security Crypto 1.0.0** для шифрования

## Лицензия

MIT License

## Контрибьюция

1. Fork проекта
2. Создайте feature branch
3. Commit изменения
4. Push в branch
5. Откройте Pull Request
