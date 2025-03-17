package com.cyanrain.baselibrary.common

interface JsonConverter<T> {

    fun toJson(any: T): String


    fun fromJson(json: String): T
}