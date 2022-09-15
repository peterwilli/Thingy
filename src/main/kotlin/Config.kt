data class HostConstraints(
    val maxSimultaneousMakeRequests: Int,
    val totalImagesInMakeCommand: Int
)

data class GRPCServer(
    val host: String,
    val port: Int,
    val plainText: Boolean
)

data class Bot(
    val name: String,
    val ownerId: String,
    val token: String,
    val hfToken: String
)

data class Timeout(
    val imageNotAppearing: Int,
    val imageNotUpdating: Int
)

data class Timeouts(
    val discoDiffusion: Timeout,
    val stableDiffusion: Timeout
)

data class Config(
    val bot: Bot,
    val maxEntriesPerOwner: Int,
    val hostConstraints: HostConstraints,
    val grpcServer: GRPCServer,
    val imagesFolder: String,
    val timeouts: Timeouts,
    val shareChannelID: String,
)