package commands.make

fun getScriptForSize(size: Int): String {
    if (size >= 768) {
        return "stable_diffusion_768"
    }
    return "stable_diffusion_512"
}