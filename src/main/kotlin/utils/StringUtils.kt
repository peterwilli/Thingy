fun String.isLettersOrDigits(): Boolean {
    for (c in this)
    {
        if (c !in 'A'..'Z' && c !in 'a'..'z' && c !in '0'..'9') {
            return false
        }
    }
    return true
}