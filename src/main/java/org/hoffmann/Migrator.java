package org.hoffmann;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.GroupApi;
import org.gitlab4j.api.ProjectApi;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.Visibility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class Migrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(Migrator.class);
    static String BITBUCKET_USERNAME;
    static String BITBUCKET_PASSWORD;
    static String GITLAB_URL;
    static String GITLAB_TOKEN;
    static List<String> GITLAB_PATHS;
    static List<String> BITBUCKET_URLS;

    /**
     * Initiates the migration process.
     */
    public static void migrate() {
        loadConfig();
        validateConfig();
        for (int i = 0; i < BITBUCKET_URLS.size(); i++) {
            LOGGER.info("Start migration " + BITBUCKET_URLS.get(i));
            Path tempDir = null;
            try {
                validateGitlabProjectPath(GITLAB_PATHS.get(i));
                tempDir = cloneRepositoryFromBitbucket(BITBUCKET_URLS.get(i), BITBUCKET_USERNAME, BITBUCKET_PASSWORD);
                Project gitlabProject = createGitLabProject(GITLAB_URL, GITLAB_TOKEN, GITLAB_PATHS.get(i));
                pushRepositoryToGitLab(tempDir, gitlabProject.getHttpUrlToRepo(), GITLAB_TOKEN);
            } catch (IOException | GitAPIException | GitLabApiException e) {
                LOGGER.error("Exception: " + e.getMessage());
            } finally {
                if (tempDir != null) {
                    deleteDirectory(tempDir.toFile());
                }
                LOGGER.info("End of migration " + BITBUCKET_URLS.get(i) + "\n");
            }
        }
    }

    /**
     * Validates the GitLab project path.
     *
     * @param projectPath the project path in Gitlab to validate
     */
    private static void validateGitlabProjectPath(String projectPath) {
        String[] parts = projectPath.split("/");
        for (String part : parts) {
            if (part.contains("_") || part.contains(".")) {
                throw new IllegalArgumentException("Group or project name cannot contain '_' or '.' characters");
            }
        }
    }

    /**
     * Clones a repository from Bitbucket.
     *
     * @param bitbucketUrl the Bitbucket repository URL
     * @param username     the Bitbucket username
     * @param password     the Bitbucket password
     * @return the path to the cloned repository
     * @throws IOException
     * @throws GitAPIException
     */
    private static Path cloneRepositoryFromBitbucket(String bitbucketUrl, String username, String password) throws IOException, GitAPIException {
        Path tempDir = Files.createTempDirectory("tempRepo_" + UUID.randomUUID());

        CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(bitbucketUrl)
                .setDirectory(tempDir.toFile())
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .setCloneAllBranches(true)
                .setMirror(true);
        Git git = cloneCommand.call();
        LOGGER.info("Repository cloned to: '" + git.getRepository().getDirectory() + "'");
        git.close();
        return tempDir;
    }

    /**
     * Creates a new groups and project in GitLab.
     *
     * @param gitlabUrl   the GitLab URL
     * @param gitlabToken the GitLab token
     * @param projectPath the project path
     * @return the created GitLab project
     * @throws GitLabApiException
     */
    private static Project createGitLabProject(String gitlabUrl, String gitlabToken, String projectPath) throws GitLabApiException {
        GroupApi groupApi;
        ProjectApi projectApi;
        try (GitLabApi gitLabApi = new GitLabApi(gitlabUrl, gitlabToken)) {
            groupApi = gitLabApi.getGroupApi();
            projectApi = gitLabApi.getProjectApi();
        }
        String[] pathParts = projectPath.split("/");
        String projectName = pathParts[pathParts.length - 1];
        StringBuilder fullPathBuilder = new StringBuilder();
        Group parentGroup = null;
        for (int i = 0; i < pathParts.length; i++) {
            String groupName = pathParts[i];
            if (i > 0) {
                fullPathBuilder.append("/");
            }
            fullPathBuilder.append(groupName);
            String fullPath = fullPathBuilder.toString();
            Group group;
            try {
                group = groupApi.getGroup(fullPath);
                LOGGER.info("Finded group: " + fullPath);
            } catch (GitLabApiException e) {
                // create new group
                LOGGER.info("Create new group: " + fullPath);
                if (parentGroup == null) {
                    group = groupApi.addGroup(groupName, groupName, "Description for " + groupName,
                            Visibility.PRIVATE, true, true, null);
                } else {
                    group = groupApi.addGroup(groupName, groupName, "Description for " + groupName,
                            Visibility.PRIVATE, true, true, parentGroup.getId());
                }
            }
            parentGroup = group;
        }
        // create new project
        try {
            Project project = new Project().withName(projectName).withNamespaceId(parentGroup.getId()).withDescription("Migrated project from Bitbucket");
            Project createdProject = projectApi.createProject(project);
            LOGGER.info("Created GitLab project: " + createdProject.getHttpUrlToRepo());
            return createdProject;
        } catch (Exception e) {
            LOGGER.info(String.format("Project %s already exist", projectName));
            return projectApi.getProject(projectPath + "/" + projectName);
        }
    }

    /**
     * Pushes a repository to GitLab.
     *
     * @param repoDir       the repository directory
     * @param gitlabRepoUrl the GitLab repository URL
     * @param gitlabToken   the GitLab token
     * @throws GitAPIException
     */
    private static void pushRepositoryToGitLab(Path repoDir, String gitlabRepoUrl, String gitlabToken) throws GitAPIException {
        try (Git git = Git.open(repoDir.toFile())) {
            git.remoteAdd().
                    setName("gitlab").
                    setUri(new org.eclipse.jgit.transport.URIish(gitlabRepoUrl)).
                    call();

            git.push()
                    .setRemote(gitlabRepoUrl)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("oauth2", gitlabToken))
                    .setPushTags()
                    .setPushAll()
                    .setForce(true)
                    .call();
            LOGGER.info("Repository pushed to " + gitlabRepoUrl);
        } catch (Exception e) {
            throw new GitAPIException("Failed to push repository: " + e.getMessage()) {
            };
        }
    }

    /**
     * Delete all temporary directory of project.
     *
     * @param file the temporary directory with project
     */
    private static void deleteDirectory(File file) {
        try {
            FileUtils.deleteDirectory(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load variable configurations from config.properties.
     */
    private static void loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = Migrator.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new IllegalArgumentException("Sorry, unable to find config.properties");
            }
            properties.load(input);
            BITBUCKET_USERNAME =
                    properties.getProperty("bitbucket.username");
            BITBUCKET_PASSWORD = properties.getProperty("bitbucket.password");
            GITLAB_URL = properties.getProperty("gitlab.url");
            GITLAB_TOKEN = properties.getProperty("gitlab.token");
            GITLAB_PATHS = Arrays.asList(properties.getProperty("gitlab.paths").split(","));
            BITBUCKET_URLS = Arrays.asList(properties.getProperty("bitbucket.urls").split(","));
        } catch (IOException e) {
            LOGGER.error("Load config exception: " + e.getMessage());
        }
    }

    /**
     * Validate all config properties variables.
     */
    private static void validateConfig() {
        if (BITBUCKET_USERNAME == null || BITBUCKET_USERNAME.isEmpty()) {
            throw new IllegalArgumentException("bitbucket.username is not set in config.properties");
        }
        if (BITBUCKET_PASSWORD == null || BITBUCKET_PASSWORD.isEmpty()) {
            throw new IllegalArgumentException("bitbucket.password is not set in config.properties");
        }
        if (GITLAB_URL == null || GITLAB_URL.isEmpty()) {
            throw new IllegalArgumentException("gitlab.url is not set in config.properties");
        }
        if (GITLAB_TOKEN == null || GITLAB_TOKEN.isEmpty()) {
            throw new IllegalArgumentException("gitlab.token is not set in config.properties");
        }
        if (GITLAB_PATHS == null || GITLAB_PATHS.isEmpty()) {
            throw new IllegalArgumentException("gitlab.paths is not set in config.properties");
        }
        if (BITBUCKET_URLS == null || BITBUCKET_URLS.isEmpty()) {
            throw new IllegalArgumentException("bitbucket.urls is not set in config.properties");
        }
        if (BITBUCKET_URLS.size() != GITLAB_PATHS.size()) {
            throw new IllegalArgumentException("The number of Bitbucket URLs and GitLab paths must be the same in config.properties");
        }
    }
}