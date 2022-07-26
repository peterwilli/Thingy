data class HostConstraints(
    val maxSimultaneousMakeRequests: Int,
    val totalImagesInMakeCommand: Int
)

data class Config(
    val botName: String,
    val botToken: String,
    val maxEntriesPerOwner: Int,
    val hostConstraints: HostConstraints
)