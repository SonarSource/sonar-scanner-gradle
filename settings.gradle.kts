rootProject.name = "sonarqube-gradle-plugin"

var isCiServer = System.getenv().containsKey("CIRRUS_CI")
var buildCacheHost = System.getenv().getOrDefault("CIRRUS_HTTP_CACHE_HOST", "localhost:12321")

buildCache {
    remote<HttpBuildCache> {
        isEnabled = isCiServer
        isPush = System.getenv()["GITHUB_BRANCH"] == "master"
        url = uri("http://${buildCacheHost}/")
    }
}
