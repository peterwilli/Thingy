package utils

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import commands.make.StableDiffusionParameters

//var strategy: ExclusionStrategy = object : ExclusionStrategy() {
//    override fun shouldSkipField(field: FieldAttributes): Boolean {
//        if (field.getDeclaringClass() === StableDiffusionParameters::class.java && field.getName().equals("")) {
//            return true
//        }
//        return false
//    }
//
//    fun shouldSkipClass(clazz: Class<*>?): Boolean {
//        return false
//    }
//}
