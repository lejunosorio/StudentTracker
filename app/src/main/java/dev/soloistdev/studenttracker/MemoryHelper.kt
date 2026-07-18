package dev.soloistdev.studenttracker

object MemoryHelper {
    fun zeroMemory(array: CharArray) {
        java.util.Arrays.fill(array, '0')
    }
}