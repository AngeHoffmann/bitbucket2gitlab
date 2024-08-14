# Bitbucket to GitLab Migration Tool
## Описание проекта
Этот проект предназначен для автоматической миграции репозиториев из Bitbucket в GitLab.
Основные функции включают клонирование репозиториев из Bitbucket, создание соответствующих проектов и групп в GitLab.
## Установка
1. Убедитесь, что у вас установлены Java и Maven.
2. Склонируйте репозиторий:
    ```bash
    git clone <URL вашего репозитория>
    cd <имя репозитория>
    ```
3. Создайте файл `config.properties` в `src/main/resources` и заполните его следующими параметрами:
    ```properties
    bitbucket.username=<Ваш Bitbucket логин>
    bitbucket.password=<Ваш Bitbucket пароль>
    gitlab.url=<URL вашего GitLab>
    gitlab.token=<Токен пользователя в гитлаб>
    gitlab.paths=<Список путей GitLab проектов через запятую>
    bitbucket.urls=<Список URL Bitbucket репозиториев через запятую>
    ```
## Использование
Запустите приложение из Main-класса или с помощью следующей команды:
```bash
mvn exec:java -Dexec.mainClass="org.hoffmann.Main"
```

## Основные классы и их функциональность
**Main**: Точка входа в приложение. Запускает процесс миграции.  
**Migrator**: Основной класс, содержащий логику миграции репозиториев.
## Основные методы и их назначение
**Main.main(String[] args):** Запускает процесс миграции.  
**Migrator.migrate():** Основной метод для выполнения миграции.  
**Migrator.loadConfig():** Загружает конфигурацию из файла config.properties.  
**Migrator.validateConfig():** Проверяет корректность конфигурации.  
**Migrator.validateGitlabProjectPath:** Проверяет корректность указанных путей в Gitlab.  
**Migrator.cloneRepositoryFromBitbucket(String bitbucketUrl, String username, String password):** Клонирует репозиторий из Bitbucket.  
**Migrator.createGitLabProject(String gitlabUrl, String gitlabToken, String projectPath):** Создает проект в GitLab со всеми необходимыми группами.  
**Migrator.pushRepositoryToGitLab(Path repoDir, String gitlabRepoUrl, String gitlabToken):** Отправляет репозиторий в GitLab.  
**Migrator.deleteDirectory(File file):** Удаляет временную директорию.  
## Взаимодействие между классами
Main вызывает метод migrate() класса Migrator для выполнения миграции.
Migrator использует библиотеки JGit для работы с Git и gitlab4j-api для взаимодействия с GitLab API.
## Требования
Java 8 или выше  
Maven 3.6.0 или выше  
Доступ к Bitbucket и GitLab
## Автор
Ange Hoffmann