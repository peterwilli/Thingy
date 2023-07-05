import java.net.URI

data class HostConstraints(
    val maxEntriesPerOwner: Int,
    val totalImagesInMakeCommand: Int
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
    val hostConstraints: HostConstraints,
    val redisHost: URI = URI.create("redis://127.0.0.1:6379"),
    val shareChannelID: String,
    val artContestChannelID: String?,
    val leaderboardChannelID: String?,
)