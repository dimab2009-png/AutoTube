# AutoTube

Клиент YouTube с входом в аккаунт: видео на парковке, аудио в Android Auto.

## Возможности
- Вход в аккаунт Google (YouTube Data API v3, только чтение) по коду на google.com/device.
- Поиск, «Лайки», ваши плейлисты, превью, выбор видео.
- Видео играет в официальном плеере YouTube (IFrame) **только когда авто стоит** (GPS < ~3.6 км/ч). Нет GPS/разрешения или есть движение — видео блокируется.
- Android Auto: аудио-каталог (`PlaybackService`), видео на экране машины недоступно.

## Ключи Google (один раз)
1. https://console.cloud.google.com → создайте проект.
2. APIs & Services → Library → **YouTube Data API v3** → Enable.
3. OAuth consent screen (Google Auth platform): тип External, добавьте свой Google-аккаунт в **Test users**.
4. Credentials → Create credentials → OAuth client ID → тип **TVs and Limited Input devices**. Скопируйте Client ID и Client secret.
5. В приложении: «Аккаунт» → «Ввести Client ID / Secret» → «Войти в YouTube», откройте google.com/device и введите код.

Ограничения: пока приложение в режиме Testing, вход нужно повторять примерно раз в 7 дней; поиск тратит квоту API (около 100 поисков в сутки).

## Сборка
Android Studio (Open → папка проекта) или GitHub Actions (`.github/workflows/build.yml`, запуск вручную: Actions → Build → Run workflow). APK — в Artifacts.

## Лицензия
MIT
