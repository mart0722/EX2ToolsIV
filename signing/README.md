# Подпись

Платформенный ключ и пароли **не входят в репозиторий**.

Для сборки системной версии положите сюда `platform.jks` и задайте параметры
через `gradle.properties` или переменные окружения:

```properties
EX2_STORE_PASSWORD=...
EX2_KEY_ALIAS=platform
EX2_KEY_PASSWORD=...
```

Без `platform.jks` проект соберётся без подписи. Обычная подпись не даст
приложению `android.uid.system` и системные разрешения.
