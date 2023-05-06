/**

 * Compare two {@code double} values

 * @param other <i>double</i> value to compare to

 * @param epsilon precision

 * @return {@code true} if the two values are equal

 */

fun Double.eq(other: Double, epsilon: Double = 0.00000001) = Math.abs(this - other) < epsilon

fun Float.eq(other: Float, epsilon: Float = 0.00000001f) = Math.abs(this - other) < epsilon