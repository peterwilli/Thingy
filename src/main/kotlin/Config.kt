data class HostConstraints(
    val maxSimultaneousMakeRequests: Int,
    val totalImagesInMakeCommand: Int
)

data class GRPCServer(
    val host: String,
    val port: Int
)

data class Timeouts(
    val imageNotAppearing: Int,
    val imageNotUpdating: Int
)

data class Config(
    val botName: String,
    val botToken: String,
    val maxEntriesPerOwner: Int,
    val hostConstraints: HostConstraints,
    val grpcServer: GRPCServer,
    val imagesFolder: String,
    val timeouts: Timeouts
)