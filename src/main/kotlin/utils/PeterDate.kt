package utils

// 🎵 Girls just wanna have fuuuun 🎵
fun peterDate(): Long {
    val birthdayTimestamp = 686790000 // Can you guess my birthday?
    val unixTimestamp = System.currentTimeMillis() / 1000
    return unixTimestamp - birthdayTimestamp
}