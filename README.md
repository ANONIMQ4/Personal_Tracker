# Personal Tracker

Personal Tracker — личный финансовый трекер для импорта операций, анализа расходов и автоматизации обработки транзакций.

## Demo

[http://5.34.208.74:8080/](http://5.34.208.74:8080/)

## Возможности

- регистрация и вход по сессии
- импорт операций из XLS/XLSX
- ручное добавление, редактирование и удаление операций
- фильтры, поиск, сортировка и экспорт CSV
- dashboard с аналитикой доходов, расходов и категорий
- Smart Rules — автоматическая обработка операций через текстовые правила

## Стек

### Backend

- Java 17+
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Apache POI

### Frontend

- HTML/CSS
- JavaScript

### AI

- OpenAI Responses API

## Smart Rules

Текстовые правила преобразуются в структурированный формат и позволяют автоматически изменять категории и обработку операций.

Примеры:

- `Ozon в Маркетплейсы`
- `кэшбэк считать доходом`
- `не учитывать переводы между своими счетами`

## Страницы

- `/` — главная страница и регистрация
- `/login` — вход
- `/myacc` — dashboard
- `/operations` — управление операциями
- `/rules` — Smart Rules

## Локальный запуск

```bash
git clone https://github.com/ANONIMQ4/Personal_Tracker.git
cd Personal_Tracker

cp .env.example .env

./mvnw spring-boot:run
```

Приложение будет доступно на:

[http://localhost:8080/](http://localhost:8080/)

## Сборка

```bash
./mvnw -DskipTests package
java -jar target/personal-tracker.jar
```
