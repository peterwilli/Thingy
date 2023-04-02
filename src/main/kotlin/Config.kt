import java.net.URI
import java.net.URL

data class HostConstraints(
    val maxSimultaneousMakeRequests: Int,
    val totalImagesInMakeCommand: Int
)

data class JCloudKeeper(
    val url: URL
)

data class Bot(
    val name: String,
    val ownerId: String,
    val token: String,
    val hfToken: String
)

data class Config(
    val bot: Bot,
    val databasePath: String,
    val maxEntriesPerOwner: Int,
    val hostConstraints: HostConstraints,
    val jcloudKeeper: JCloudKeeper?,
    val directUrl: URI?,
    val imagesFolder: String,
    val shareChannelID: String,
    val artContestChannelID: String?,
    val leaderboardChannelID: String?,
)