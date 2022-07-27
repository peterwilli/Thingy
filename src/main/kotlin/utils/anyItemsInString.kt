package utils

fun anyItemsInString(string: String, items: List<String>): Boolean {
    for (item in items) {
        if (string.indexOf(item) > -1) {
            return true
        }
    }
    return false
}