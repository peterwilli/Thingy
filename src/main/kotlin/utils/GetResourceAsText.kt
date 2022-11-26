package utils

fun getResourceAsText(path: String): String =
    object {}.javaClass.getResource(path)?.readText()!!