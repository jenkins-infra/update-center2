package org.jvnet.hudson.update_center;

public class DefaultMavenRepositoryBuilder {

    private static String ARTIFACTORY_API_USERNAME = System.getenv("ARTIFACTORY_USERNAME");
    private static String ARTIFACTORY_API_PASSWORD = System.getenv("ARTIFACTORY_PASSWORD");

    private DefaultMavenRepositoryBuilder () {
        
    }

    private static BaseMavenRepository instance;
    
    public static BaseMavenRepository getInstance() throws Exception {
        if (instance == null) {
            if (ARTIFACTORY_API_PASSWORD != null && ARTIFACTORY_API_USERNAME != null) {
                instance = new ArtifactoryRepositoryImpl(ARTIFACTORY_API_USERNAME, ARTIFACTORY_API_PASSWORD);
            } else {
                throw new IllegalStateException("ARTIFACTORY_USERNAME and ARTIFACTORY_PASSWORD need to be set");
            }
        }
        return instance;
    }
}
