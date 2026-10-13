# GitLab production deployment

Pipeline собирает и проверяет backend на общем Docker runner, публикует образы в GitLab Container Registry и вручную разворачивает весь стек на целевой VM через shell runner.

## GitLab settings

1. Сделайте default branch protected и разрешите merge только через проверенный pipeline.
2. В проекте откройте Settings → CI/CD → Runners → New project runner, задайте тег `etsopy-deploy`, отключите `Run untagged jobs` и включите `Protected`. Полученный `glrt-*` token передайте администратору для поля runner token при создании VM из шаблона «Приложение (Docker) + deploy-раннер». Шаблон сам регистрирует shell runner, добавляет `gitlab-runner` в группу `docker` и подготавливает `/srv/app`; SSH для настройки не нужен.
3. Создайте protected file variable `ETSOPY_PROD_ENV`. Содержимое возьмите из `.env.example`, заменив все `changeme_*`, задав `JWT_SECRET` длиной не менее 256 бит и `JWT_ISSUER_URI=https://etsopy.kseoni.ch`.
4. Если нужен SonarQube, добавьте protected masked variables `SONAR_HOST_URL` и `SONAR_TOKEN`.

Стандартные `CI_REGISTRY_*` переменные GitLab используются автоматически. Для deploy runner нужен исходящий доступ к GitLab и `registry.kseoni.ch`.

## Network and proxy

Разрешите вход на VM к порту `8080` только от Nginx Proxy Manager. В NPM создайте proxy host `etsopy.kseoni.ch` на `http://<VM-IP>:8080`, включите WebSocket Support и TLS. Служебные порты БД, брокеров и микросервисов не публикуйте в интернет.

## Release procedure

После merge в default branch дождитесь успешных `verify:maven` и `images:publish`, затем вручную запустите `deploy:production`. Job создаёт `/srv/app/etsopy`, формирует постоянный `.env` из `ETSOPY_PROD_ENV` и текущего commit SHA, скачивает образы, ждёт health checks и проверяет регистрацию девяти доменных сервисов в Eureka. Значения `REGISTRY_IMAGE` и `IMAGE_TAG` добавляются job автоматически; включать их в `ETSOPY_PROD_ENV` не нужно.

Проверка после deploy:

```bash
curl -fsS https://etsopy.kseoni.ch/actuator/health/readiness
docker compose -f /srv/app/etsopy/docker-compose.prod.yml ps
```

Для отката повторно запустите manual deploy job нужного успешного commit. Он запишет SHA этого commit в постоянный `.env`; данные сохраняются в named Docker volumes.
