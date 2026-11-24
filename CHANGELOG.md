# Changelog

## 0.1.0 (2026-11-24)


### Features

* scaffold backend, frontend and checks ([cd80024](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/cd80024d2f488b446f81c084e517e322c265fac1))
* бронирование звонков ([a3457d6](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/a3457d69d9652d1ea4e552d740bba781c3718d7c))
* главная страница со ссылкой на страницу записи ([b5adb47](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/b5adb47acf2edeb3d862320c4e8fec24c71e6b74))
* добавить в контракт создание типа встречи ([#15](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/15)) ([ac223bf](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/ac223bf482da7037a984b4a9cdd5bfba0471e880))
* добавить в контракт список типов встреч ([#14](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/14)) ([c1b35d0](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/c1b35d048a3f6197d7abaec7af99586b355ea87a))
* записывать гостя на слот через POST /api/bookings ([#18](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/18)) ([fe663f7](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/fe663f72eb23d1461173a04fef354ec46409f10d))
* запрещать пересечение встреч и отдавать 409 при конфликте ([#19](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/19)) ([22e2a00](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/22e2a0047df9815d3af4daeabcb6d9a5f995b74a))
* отдавать свободные слоты на 14 дней и показывать их на странице записи ([#16](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/16)) ([7f8f039](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/7f8f039603dfaa27c18052052b8709f0cc2758d4))
* показать типы встреч на странице записи ([#14](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/14)) ([c23e1b0](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/c23e1b0f8ec33bc40b0971a5494a4374919eccdb))
* создавать тип встречи через POST /api/event-types с проверкой полей и 409 на дубль ([#15](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/15)) ([7daf812](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/7daf812d7640f26cc260594901f5d5af7dcf1899))
* список предстоящих встреч владельца в /owner ([#20](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/20)) ([5f27552](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/5f27552caa58b3492b3139b76c868d1670bd1cab))
* форма гостя на странице записи и страница подтверждения ([#18](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/18)) ([6727b19](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/6727b191ba41cd3bc27e32508ff4851e25978cd6))
* форма создания типа встречи в разделе владельца /owner ([#15](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/15)) ([7cd599b](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/7cd599b2a15ae2d6ab9b47ff9714079327e709fb))
* хранить типы встреч во встроенной БД и отдавать их по GET /api/event-types ([#14](https://github.com/bahaalmkz/ai-for-developers-project-386/issues/14)) ([b49ecdf](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/b49ecdf12050eba7135b834b4b84fdbea42ef7b3))


### Bug Fixes

* 400 с fieldErrors для пропущенных полей и разбор мусорного JSON ([ec0b931](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/ec0b9319b1a7f72ea337535caf1db67177be6ccf))
* взять путь к файлу сообщения коммита в кавычки в хуке commitlint ([a5f38f2](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/a5f38f20a93fc19ac9b7360b7b94e647221dcc37))
* исправить находки финального ревью feat/booking ([e6f935f](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/e6f935f73561c4076fcca301dff7af734ea3bb96))
* общее сообщение при 400 без полей и защита от гонки слотов ([cae2339](https://github.com/bahaalmkz/ai-for-developers-project-386/commit/cae23393506bc041ae9e6d55f39dc80761841d8a))
