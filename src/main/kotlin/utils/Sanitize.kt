package utils

fun sanitize(txt: String): String {
    return txt.replace("*", "\\*").replace("`", "\\`").replace("|", "\\|")
}