# GPS Tracker — реализация по ТЗ

## Что уже сделано (Шаги 1–5)

1. **Разрешения и манифест** — `AndroidManifest.xml`
2. **Foreground Service** — `service/TrackingService.kt`
   - FusedLocationProviderClient, priority HIGH_ACCURACY, interval 2с
   - Persistent notification с live-метриками
   - Запись точек в Room "на лету"
3. **Фильтрация GPS** — `util/LocationFilter.kt`
   - Отсечение по accuracy > 15м
   - Экспоненциальное сглаживание координат + отсев "телепортаций"
4. **Дашборд и карта в реальном времени** — `ui/dashboard/`
   - GoogleMap (Maps Compose), маркер, live-полилиния, метрики, индикатор потери GPS
5. **История и детализация поездок** — `ui/history/`
   - Список поездок из Room
   - Детальный экран с цветными сегментами маршрута по скорости

## Шаг 6 — оптимизация под Samsung Galaxy Note 20 / Android 13 / One UI 5.1

Добавлено под конкретный таргет-девайс:

- **`util/BatteryOptimizationHelper.kt`** — запрос системного исключения из
  battery optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
  Важно: у One UI поверх этого есть свой список "Спящих приложений" в
  Device Care, для которого нет публичного API — пользователя нужно направить
  туда вручную (кнопка "Открыть Device Care вручную" в онбординге).
- **`ui/onboarding/OnboardingScreen.kt`** — пошаговый (не одновременный) запрос
  разрешений: fine location → background location → notifications (Android 13+)
  → battery exemption. На Android 13 система физически не даёт запросить
  fine и background location одним диалогом, поэтому шаги разделены.
- **`ui/settings/SettingsScreen.kt`** — переключение языка RU/EN через
  `LocaleHelper` (per-app language API, доступен из коробки на Android 13).
- `MainActivity` теперь показывает `OnboardingScreen` при первом запуске и
  переключается на `AppNavGraph` (с новой вкладкой "Настройки") после
  прохождения всех шагов.

### Что стоит проверить вручную на реальном Note 20

- После первого запуска и выдачи всех разрешений — свернуть приложение,
  подождать 10-15 минут и проверить, что уведомление о записи и сама запись
  трека не оборвались (Device Care иногда всё равно "усыпляет" приложение
  даже после снятия battery optimization — тогда нужно вручную зайти в
  Настройки → Уход за устройством → Батарея → Ограничения фона и добавить
  приложение в исключения).
- One UI 5.1 может показывать собственный диалог "Разрешить фоновую активность"
  при первом старте foreground-сервиса — это отдельно от Android-диалогов.

## Шаг 7 — Полный отказ от Google Maps SDK, переход на MapLibre + офлайн-карты

Это архитектурное изменение, а не косметическое: карта, рендер трека и вся
работа с сетью для тайлов поменялись. Что сделано:

### Замена движка карты
- Убраны `com.google.android.gms:play-services-maps` и `maps-compose`, ключ
  Google Maps API удалён из манифеста.
- Добавлен `org.maplibre.gl:android-sdk` — открытый форк Mapbox GL без
  привязки к Google. У MapLibre нет готового Compose API, поэтому
  `ui/maps/MapLibreTrackMap.kt` оборачивает нативный `MapView` через
  `AndroidView` + вручную привязывает жизненный цикл (`onCreate/onResume/.../onDestroy`).
- Раскраска сегментов трека по скорости теперь делается через GeoJSON-слой
  (`LineLayer` с data-driven expression `["get","color"]`), а не через
  прямой список `Polyline`, как было в Google Maps Compose — сама логика
  расчёта цвета в `SpeedColorUtil.kt` не изменилась.
- **`play-services-location` (FusedLocationProviderClient) оставлен** — это
  чистый location-API, не завязанный на Google Maps, и он даёт лучшую точность/
  энергоэффективность, чем голый `LocationManager`. Если нужен и его убрать —
  скажи отдельно, это отдельная замена в `TrackingService`.

### Офлайн-карты (новый модуль)
- **`util/GeoUtils.kt`** — расчёт BBox по радиусу (формулы из доп. ТЗ).
- **`offline/OfflineMapManager.kt`** — обёртка над встроенным
  `OfflineManager` MapLibre: скачивание bbox-региона, прогресс через `Flow`,
  список/удаление регионов. Это и есть рекомендованный ТЗ "Вариант А" —
  своих `.mbtiles`/тайл-сервер поднимать не нужно, MapLibre кэширует тайлы
  сам при обычной отрисовке стиля.
- **`offline/OfflineDownloadWorker.kt`** — `CoroutineWorker` с
  `ForegroundInfo` (прогресс-бар в шторке) и `Constraints` на Wi-Fi-only.
  При ошибке возвращает `Result.retry()` — WorkManager сам повторит попытку.
- **`offline/RegionCatalog.kt`** — ⚠️ **это заглушка**: 5 захардкоженных
  регионов с примерным размером. Настоящий каталог "Страна → Область" с
  реальными границами (Geofabrik/Protomaps) требует своего JSON-индекса —
  структура (`RegionCatalogEntry`) уже готова принять данные с сервера,
  нужно только заменить статический список на сетевой запрос.
- **`ui/offlinemaps/OfflineMapsScreen.kt`** — новая вкладка "Карты": кнопки
  "Радиус 50 км" / "Выбрать регион", список загруженного с размером на диске
  и кнопкой удаления, переключатель "только Wi-Fi".
- **Room**: добавлена таблица `offline_regions` (версия БД 2, сейчас
  `fallbackToDestructiveMigration()` — для продакшн-релиза заменить на
  настоящую `Migration(1, 2)`, иначе апдейт приложения сотрёт локальные поездки).
- **`util/NetworkUtils.kt`** — проверка интернета для индикатора "офлайн-режим"
  на дашборде. Сам MapLibre переключается на кэш прозрачно, если тайлы для
  области уже скачаны — никакого ручного переключения TileProvider не требуется.

### Что нужно доделать/решить отдельно
- **Стиль карты**: сейчас `DEFAULT_STYLE_URL` = публичный демо-стиль MapLibre
  (`demotiles.maplibre.org`) — годится для теста, но для реального продакшена
  нужен либо платный вектор-тайл провайдер (MapTiler, Stadia Maps и т.п.),
  либо свой self-hosted tile server (например, TileServer GL с
  сгенерированными из OSM `.mbtiles`). Без этого шага скачивание "офлайн"
  регионов будет кэшировать демо-стиль с минимальной детализацией.
- **Каталог регионов** — см. выше, сейчас статический список-заглушка.
- **Маркер текущей позиции** — сейчас простой кружок (`CircleLayer`), не
  иконка-стрелка с направлением движения, как обычно в трекерах.
- **Удаление региона из MapLibre** — `OfflineMapsViewModel.deleteRegion()`
  сейчас удаляет только запись в Room; реальные тайлы в базе MapLibre
  (`OfflineRegion.delete()`) нужно чистить отдельно по `maplibreRegionId`
  — сейчас это оставлено как TODO, иначе накопится "мёртвый" вес на диске.

- Заменить `DEFAULT_STYLE_URL` в `offline/OfflineMapManager.kt` на боевой тайл-провайдер/свой стиль
- Добавить иконку `@mipmap/ic_launcher` (стандартный Android Studio wizard создаёт её автоматически)
- Обработка полного отказа в разрешении (сейчас онбординг просто останется на
  экране запроса — можно добавить экран-заглушку "функция недоступна без разрешения")
- Миграции Room при последующих изменениях схемы (сейчас `version = 1`)

## Задачи 1–6 — закрыты

1. **Каскадное удаление офлайн-регионов** — `OfflineMapManager.deleteRegionById()` + `OfflineMapsViewModel.deleteRegion()`: сначала удаляются тайлы из MapLibre, запись Room стирается только при успехе; ошибка показывается диалогом.
2. **Боевой провайдер карт** — `util/MapConfig.kt`, ключ MapTiler через `BuildConfig.MAPTILER_API_KEY` (см. `gradle.properties`/`gradle.properties.example`). Без ключа — fallback на демо-стиль для локальной отладки.
3. **Реальный каталог регионов** — `assets/region_catalog.json` + `offline/RegionCatalogRepository.kt`, отдаёт `Flow<List<RegionCatalogEntry>>`, читает файл асинхронно на `Dispatchers.IO`.
4. **Location Puck с азимутом** — `ui/maps/MapLibreTrackMap.kt` использует нативный `LocationComponent` (`RenderMode.COMPASS`, `useDefaultLocationEngine(false)`), координаты и bearing прокидываются вручную из `TrackingService` через `forceLocationUpdate()`.
5. **Безопасная миграция Room** — `data/local/Migrations.kt` (`MIGRATION_1_2`), `fallbackToDestructiveMigration()` убран из `AppDatabase`.
6. **Окончательный отказ в разрешениях** — `util/PermissionPrefs.kt` отличает первый отказ от перманентного через сохранённый флаг + `shouldShowRequestPermissionRationale`; `OnboardingScreen.kt` показывает экран-объяснение с кнопкой в системные настройки и пересчитывает состояние при возврате (`ON_RESUME`).

### Что стоит проверить/донастроить

- В `gradle.properties` — реальный ключ MapTiler (бесплатный тариф — https://www.maptiler.com/cloud/)
- `RegionCatalogRepository` берёт данные из статического ассета — при желании перевести на сетевой JSON-индекс достаточно поменять `loadCatalog()`
- Плагин `android-plugin-locationcomponent-v9` — версия `3.0.2` указана по последнему релизу на момент написания, проверить актуальность при Gradle sync

## Финальный аудит — защита данных и полировка

1. **Room DB — железобетонная защита**: `fallbackToDestructiveMigration()` отсутствует физически, апгрейд версии идёт только через `addMigrations(MIGRATION_1_2)`. Добавлен `fallbackToDestructiveMigrationOnDowngrade()`, но он активен **только в DEBUG-билде** и срабатывает лишь при откате версии схемы назад (немыслимо в релизе, где `versionCode` всегда растёт) — защищает исключительно от локальных экспериментов с даунгрейдом debug-сборки.
2. **Непрерывность записи при `START_STICKY`** — `util/TrackingPrefs.kt` хранит `trip_id` и время старта активной поездки в `SharedPreferences`. `TrackingService.onStartCommand()` различает явный `ACTION_START` (новая поездка) от `intent == null` (перезапуск системой) — во втором случае вызывается `resumeAfterSystemRestart()`, которая продолжает писать точки в тот же `trip_id`, а не создаёт рваный кусок.
3. **Мгновенная фиксация точек** — `TrackingRepository.savePoint()` теперь оборачивает вставку в `db.withTransaction { }` и `withContext(NonCancellable + Dispatchers.IO)`: если `serviceScope` отменяется в момент убийства процесса, уже запущенная запись всё равно долетает до диска.
4. **Уведомление сервиса** — `NotificationCompat.Builder` теперь явно задаёт `setCategory(CATEGORY_SERVICE)` и `setPriority(PRIORITY_LOW)`, чтобы система не понижала приоритет процесса из-за "непонятного" уведомления.
5. **Стабилизация азимута puck-а** — в `MapLibreTrackMap.kt` bearing из GPS используется только при `speed >= 1.0f м/с`; ниже порога стрелка держит последний стабильный курс (`stableBearing`) вместо дребезга на светофоре/остановке.
6. **README** — убрано упоминание Google Maps API key из инструкции сборки, шаги актуализированы под MapLibre + MapTiler.



1. Открыть папку в Android Studio (Koala/Ladybird и новее)
2. Скопировать `gradle.properties.example` в `gradle.properties` (если ещё не сделано) и подставить реальный ключ `MAPTILER_API_KEY` (бесплатный тариф — https://www.maptiler.com/cloud/)
3. Дождаться Gradle sync
4. Запустить на устройстве/эмуляторе с Google Play Services (API 26+) — Play Services нужны только для `FusedLocationProviderClient`, карта (MapLibre) от них не зависит
